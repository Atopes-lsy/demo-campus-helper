package com.campus.helper.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.helper.entity.Order;
import com.campus.helper.entity.OrderTracking;
import com.campus.helper.entity.PricingRule;
import com.campus.helper.entity.Runner;
import com.campus.helper.mapper.OrderMapper;
import com.campus.helper.mapper.OrderTrackingMapper;
import com.campus.helper.mapper.PricingRuleMapper;
import com.campus.helper.mapper.RunnerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchService {

    private final RunnerMapper runnerMapper;
    private final OrderMapper orderMapper;
    private final OrderTrackingMapper orderTrackingMapper;
    private final PricingRuleMapper pricingRuleMapper;
    private final NotificationService notificationService;
    private final RedissonClient redissonClient;

    private static final double DEFAULT_DISTANCE_WEIGHT = 0.4;
    private static final double DEFAULT_RATING_WEIGHT = 0.3;
    private static final double DEFAULT_WORKLOAD_WEIGHT = 0.3;
    private static final int MAX_RADIUS = 3000;
    private static final double MIN_SCORE_THRESHOLD = 0.3;
    private static final int EARTH_RADIUS = 6371000;

    /**
     * Dispatch an order to the best available runner using a multi-factor scoring algorithm.
     * Uses a distributed lock to prevent concurrent dispatch of the same order.
     *
     * @param orderId the order to dispatch
     * @return the assigned runner, or null if no suitable runner found
     */
    public Runner dispatch(Long orderId) {
        RLock lock = redissonClient.getLock("dispatch:order:" + orderId);
        try {
            boolean acquired = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("Failed to acquire dispatch lock for order {}: lock held by another thread", orderId);
                return null;
            }

            // Get order and verify status is pending (0)
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                log.warn("Order {} not found", orderId);
                return null;
            }
            if (order.getStatus() != 0) {
                log.warn("Order {} status is {}, expected 0 (pending)", orderId, order.getStatus());
                return null;
            }

            // Load dispatch weights from active pricing rules, with defaults
            double distW = DEFAULT_DISTANCE_WEIGHT;
            double ratW = DEFAULT_RATING_WEIGHT;
            double workW = DEFAULT_WORKLOAD_WEIGHT;

            List<PricingRule> activeRules = pricingRuleMapper.selectList(
                    new LambdaQueryWrapper<PricingRule>()
                            .eq(PricingRule::getIsActive, 1)
            );
            for (PricingRule rule : activeRules) {
                if ("DISPATCH_WEIGHT".equals(rule.getConditionType()) && rule.getPriceFactor() != null) {
                    String expr = rule.getConditionExpr();
                    if ("distance_weight".equals(expr)) {
                        distW = rule.getPriceFactor().doubleValue();
                    } else if ("rating_weight".equals(expr)) {
                        ratW = rule.getPriceFactor().doubleValue();
                    } else if ("workload_weight".equals(expr)) {
                        workW = rule.getPriceFactor().doubleValue();
                    }
                }
            }
            // Final copies for lambda capture
            final double distanceWeight = distW;
            final double ratingWeight = ratW;
            final double workloadWeight = workW;
            log.info("Dispatch weights for order {}: distance={}, rating={}, workload={}",
                    orderId, distanceWeight, ratingWeight, workloadWeight);

            // Query available runners (status=0 means free)
            List<Runner> availableRunners = runnerMapper.selectList(
                    new LambdaQueryWrapper<Runner>().eq(Runner::getStatus, 0)
            );

            if (availableRunners.isEmpty()) {
                log.info("No available runners for order {}", orderId);
                return null;
            }

            // Calculate score for each runner and sort descending
            List<RunnerScore> scored = availableRunners.stream()
                    .map(runner -> {
                        double distanceScore = calcDistanceScore(order, runner);
                        double ratingScore = calcRatingScore(runner);
                        double workloadScore = calcWorkloadScore(runner);
                        double total = distanceWeight * distanceScore
                                + ratingWeight * ratingScore
                                + workloadWeight * workloadScore;
                        return new RunnerScore(runner, total);
                    })
                    .sorted(Comparator.comparingDouble(RunnerScore::getScore).reversed())
                    .collect(Collectors.toList());

            RunnerScore best = scored.get(0);
            log.info("Best runner for order {}: runnerId={}, name={}, score={}",
                    orderId, best.getRunner().getId(), best.getRunner().getName(), best.getScore());

            // Reject if best score below threshold
            if (best.getScore() < MIN_SCORE_THRESHOLD) {
                log.info("No suitable runner for order {}: best score {} < threshold {}",
                        orderId, best.getScore(), MIN_SCORE_THRESHOLD);
                return null;
            }

            Runner runner = best.getRunner();

            // Update order: assign runner and set status to 1 (dispatched)
            order.setRunnerId(runner.getId());
            order.setStatus(1);
            order.setUpdatedAt(LocalDateTime.now());
            orderMapper.updateById(order);

            // Update runner: set status to 1 (busy)
            runner.setStatus(1);
            runner.setUpdatedAt(LocalDateTime.now());
            runnerMapper.updateById(runner);

            // Insert tracking record
            OrderTracking tracking = new OrderTracking();
            tracking.setOrderId(orderId);
            tracking.setStatusFrom(0);
            tracking.setStatusTo(1);
            tracking.setDescription("系统自动派单给" + runner.getName());
            tracking.setOperatorId(null);
            tracking.setOperatorType("SYSTEM");
            tracking.setCreatedAt(LocalDateTime.now());
            orderTrackingMapper.insert(tracking);

            // Send notifications to both student and runner
            notificationService.sendDispatchNotification(order, runner);

            log.info("Order {} dispatched to runner {} ({})", orderId, runner.getId(), runner.getName());
            return runner;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Dispatch interrupted for order {}", orderId, e);
            return null;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * Calculate distance score using haversine formula.
     * If runner position is null, defaults to 0.5.
     * Score = max(0, 1 - distance / maxRadius).
     */
    private double calcDistanceScore(Order order, Runner runner) {
        if (runner.getLatitude() == null || runner.getLongitude() == null
                || order.getPickupLat() == null || order.getPickupLng() == null) {
            return 0.5;
        }
        double distance = haversine(
                runner.getLatitude().doubleValue(),
                runner.getLongitude().doubleValue(),
                order.getPickupLat().doubleValue(),
                order.getPickupLng().doubleValue()
        );
        return Math.max(0.0, 1.0 - distance / MAX_RADIUS);
    }

    /**
     * Calculate rating score: runner rating / 5.0.
     */
    private double calcRatingScore(Runner runner) {
        if (runner.getRating() == null) {
            return 0.5;
        }
        return runner.getRating().doubleValue() / 5.0;
    }

    /**
     * Calculate workload score based on current active order count vs max concurrent.
     * Score = max(0, 1 - currentLoad / maxConcurrent).
     */
    private double calcWorkloadScore(Runner runner) {
        Long currentLoad = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getRunnerId, runner.getId())
                        .in(Order::getStatus, List.of(1, 2, 3))
        );
        int maxConcurrent = runner.getMaxConcurrent() != null && runner.getMaxConcurrent() > 0
                ? runner.getMaxConcurrent() : 3;
        return Math.max(0.0, 1.0 - (double) currentLoad / maxConcurrent);
    }

    /**
     * Haversine formula to calculate the great-circle distance between two points.
     *
     * @param lat1 latitude of point 1 in degrees
     * @param lng1 longitude of point 1 in degrees
     * @param lat2 latitude of point 2 in degrees
     * @param lng2 longitude of point 2 in degrees
     * @return distance in meters
     */
    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double sinDLat = Math.sin(dLat / 2);
        double sinDLng = Math.sin(dLng / 2);
        double a = sinDLat * sinDLat
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * sinDLng * sinDLng;
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }

    /**
     * Internal holder for a runner-score pair, used for sorting.
     */
    private static class RunnerScore {
        private final Runner runner;
        private final double score;

        RunnerScore(Runner runner, double score) {
            this.runner = runner;
            this.score = score;
        }

        Runner getRunner() {
            return runner;
        }

        double getScore() {
            return score;
        }
    }
}
