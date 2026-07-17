package com.campus.helper.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.helper.entity.DeliveryDistanceMatrix;
import com.campus.helper.entity.Order;
import com.campus.helper.entity.OrderTracking;
import com.campus.helper.mapper.DeliveryDistanceMatrixMapper;
import com.campus.helper.mapper.OrderMapper;
import com.campus.helper.mapper.OrderTrackingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes and maintains the delivery distance/time matrix by analyzing
 * actual delivery times from completed orders.
 *
 * Data flow:
 * 1. Query completed orders (status=5)
 * 2. For each order, find tracking records: PICKED_UP (status_to=3) → DELIVERED (status_to=4)
 * 3. Calculate time difference
 * 4. Group by pickup_location × delivery_location
 * 5. Compute avg/median/min/max for each route
 * 6. Upsert into delivery_distance_matrix table
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryMatrixService {

    private final OrderMapper orderMapper;
    private final OrderTrackingMapper orderTrackingMapper;
    private final DeliveryDistanceMatrixMapper matrixMapper;

    /**
     * Refresh the delivery matrix every hour at minute 15.
     */
    @Scheduled(cron = "0 15 * * * ?")
    public void scheduledRefresh() {
        log.info("Scheduled delivery matrix refresh started");
        refreshMatrix();
        log.info("Scheduled delivery matrix refresh completed");
    }

    /**
     * Manually refresh the delivery distance matrix.
     * Called from the admin API endpoint.
     */
    public void refreshMatrix() {
        LocalDateTime ninetyDaysAgo = LocalDateTime.now().minusDays(90);
        // Query all completed orders in the last 90 days
        List<Order> completedOrders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getStatus, 5)
                        .ge(Order::getCompletedAt, ninetyDaysAgo)
        );
        log.info("Found {} completed orders for matrix computation", completedOrders.size());

        // Map: "pickup→delivery" → list of durations in minutes
        Map<String, List<Double>> routeDurations = new LinkedHashMap<>();

        for (Order order : completedOrders) {
            String pickup = order.getPickupLocation();
            String delivery = order.getDeliveryLocation();
            if (pickup == null || delivery == null || pickup.isBlank() || delivery.isBlank()) {
                continue;
            }

            // Find tracking records for this order: picked up and delivered
            List<OrderTracking> trackings = orderTrackingMapper.selectList(
                    new LambdaQueryWrapper<OrderTracking>()
                            .eq(OrderTracking::getOrderId, order.getId())
                            .orderByAsc(OrderTracking::getCreatedAt)
            );

            LocalDateTime pickupTime = null;
            LocalDateTime deliveryTime = null;

            for (OrderTracking t : trackings) {
                if (t.getStatusTo() != null && t.getStatusTo() == 3 && pickupTime == null) {
                    pickupTime = t.getCreatedAt();
                }
                if (t.getStatusTo() != null && t.getStatusTo() == 4 && deliveryTime == null) {
                    deliveryTime = t.getCreatedAt();
                }
            }

            // Fallback: if no tracking records with those statuses, use order timestamps
            if (pickupTime == null && order.getUpdatedAt() != null) {
                pickupTime = order.getUpdatedAt();
            }
            if (deliveryTime == null && order.getCompletedAt() != null) {
                deliveryTime = order.getCompletedAt();
            }

            if (pickupTime == null || deliveryTime == null) {
                continue;
            }

            double minutes = Duration.between(pickupTime, deliveryTime).toMinutes();
            if (minutes <= 0 || minutes > 180) {
                // Skip unreasonable durations (<= 0 or > 3 hours)
                continue;
            }

            String routeKey = pickup + "→" + delivery;
            routeDurations.computeIfAbsent(routeKey, k -> new ArrayList<>()).add(minutes);
        }

        // Compute statistics and upsert
        int upserted = 0;
        for (Map.Entry<String, List<Double>> entry : routeDurations.entrySet()) {
            String routeKey = entry.getKey();
            List<Double> durations = entry.getValue();
            String[] parts = routeKey.split("→", 2);
            if (parts.length != 2) continue;

            String pickup = parts[0].trim();
            String delivery = parts[1].trim();

            // Sort for median calculation
            Collections.sort(durations);
            double avg = durations.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double min = durations.get(0);
            double max = durations.get(durations.size() - 1);
            double median;
            int mid = durations.size() / 2;
            if (durations.size() % 2 == 0) {
                median = (durations.get(mid - 1) + durations.get(mid)) / 2.0;
            } else {
                median = durations.get(mid);
            }

            // Upsert: try find existing, update or insert
            DeliveryDistanceMatrix existing = matrixMapper.selectByRoute(pickup, delivery);
            DeliveryDistanceMatrix matrix = existing != null ? existing : new DeliveryDistanceMatrix();
            matrix.setPickupLocation(pickup);
            matrix.setDeliveryLocation(delivery);
            matrix.setAvgMinutes(BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP));
            matrix.setMedianMinutes(BigDecimal.valueOf(median).setScale(1, RoundingMode.HALF_UP));
            matrix.setMinMinutes(BigDecimal.valueOf(min).setScale(1, RoundingMode.HALF_UP));
            matrix.setMaxMinutes(BigDecimal.valueOf(max).setScale(1, RoundingMode.HALF_UP));
            matrix.setOrderCount(durations.size());
            matrix.setUpdatedAt(LocalDateTime.now());

            if (existing != null) {
                matrixMapper.updateById(matrix);
            } else {
                matrixMapper.insert(matrix);
            }
            upserted++;
        }

        log.info("Delivery matrix refreshed: {} routes upserted from {} completed orders",
                upserted, completedOrders.size());
    }

    /**
     * Get the estimated delivery time for a given route.
     *
     * @param pickupLocation  the pickup address
     * @param deliveryLocation the delivery address
     * @return the matrix row, or null if no data
     */
    public DeliveryDistanceMatrix getETA(String pickupLocation, String deliveryLocation) {
        DeliveryDistanceMatrix result = matrixMapper.selectByRoute(pickupLocation, deliveryLocation);
        if (result == null) {
            // Try fuzzy: check if delivery matches a known pattern
            result = matrixMapper.selectByPickup(pickupLocation).stream()
                    .map(m -> matrixMapper.selectByRoute(
                            (String) m.get("pickup_location"),
                            (String) m.get("delivery_location")))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        return result;
    }

    /**
     * Get the full matrix overview for display.
     */
    public List<Map<String, Object>> getMatrixOverview() {
        return matrixMapper.selectMatrixOverview();
    }

    /**
     * Get ETA description text for use in AI responses.
     */
    public String getETADescription(String pickupLocation, String deliveryLocation) {
        DeliveryDistanceMatrix eta = getETA(pickupLocation, deliveryLocation);
        if (eta != null) {
            return String.format(
                    "路线「%s → %s」：平均 %.0f 分钟，最快 %.0f 分钟，最慢 %.0f 分钟（基于 %d 单历史数据）",
                    eta.getPickupLocation(), eta.getDeliveryLocation(),
                    eta.getAvgMinutes(), eta.getMinMinutes(), eta.getMaxMinutes(),
                    eta.getOrderCount()
            );
        }
        // Try partial match on pickup only
        List<Map<String, Object>> fromPickup = matrixMapper.selectByPickup(pickupLocation);
        if (!fromPickup.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("找到从「").append(pickupLocation).append("」出发的历史数据：\n");
            for (Map<String, Object> row : fromPickup) {
                sb.append("- 到「").append(row.get("delivery_location")).append("」：平均 ")
                        .append(row.get("avg_minutes")).append(" 分钟\n");
            }
            return sb.toString().trim();
        }
        return null;
    }
}
