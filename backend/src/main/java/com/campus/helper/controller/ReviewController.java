package com.campus.helper.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.helper.common.BusinessException;
import com.campus.helper.common.Result;
import com.campus.helper.entity.Order;
import com.campus.helper.entity.Review;
import com.campus.helper.entity.Runner;
import com.campus.helper.mapper.OrderMapper;
import com.campus.helper.mapper.ReviewMapper;
import com.campus.helper.mapper.RunnerMapper;
import com.campus.helper.security.AuthenticatedUser;
import com.campus.helper.security.SecurityUtils;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewMapper reviewMapper;
    private final OrderMapper orderMapper;
    private final RunnerMapper runnerMapper;

    public ReviewController(ReviewMapper reviewMapper,
                            OrderMapper orderMapper,
                            RunnerMapper runnerMapper) {
        this.reviewMapper = reviewMapper;
        this.orderMapper = orderMapper;
        this.runnerMapper = runnerMapper;
    }

    /**
     * Submit a review for a completed order.
     * Updates the runner's average rating.
     */
    @PostMapping
    public Result submit(@RequestBody Map<String, Object> body, Authentication authentication) {
        AuthenticatedUser user = SecurityUtils.current(authentication);
        Long orderId = body.get("orderId") != null
                ? ((Number) body.get("orderId")).longValue() : null;
        Integer reviewerType = "STUDENT".equals(user.role()) ? 1
                : "RUNNER".equals(user.role()) ? 2 : null;
        Integer rating = body.get("rating") != null
                ? ((Number) body.get("rating")).intValue() : null;
        String content = (String) body.get("content");
        String tags = (String) body.get("tags");

        if (orderId == null || reviewerType == null || rating == null) {
            throw new BusinessException(400, "orderId, reviewerType, rating 不能为空");
        }
        if (rating < 1 || rating > 5) {
            throw new BusinessException(400, "评分范围1-5");
        }

        // Verify order exists and is completed
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        if (order.getStatus() != 5) {
            throw new BusinessException(400, "只能评价已完成的订单");
        }
        boolean ownsOrder = reviewerType == 1 && user.userId().equals(order.getStudentId())
                || reviewerType == 2 && user.userId().equals(order.getRunnerId());
        if (!ownsOrder) {
            throw new BusinessException(403, "无权评价该订单");
        }

        // Check duplicate: one review per reviewer_type per order
        Long count = reviewMapper.selectCount(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getOrderId, orderId)
                        .eq(Review::getReviewerType, reviewerType));
        if (count > 0) {
            throw new BusinessException(400, "该订单已经评价过了");
        }

        // Insert review
        Review review = new Review();
        review.setOrderId(orderId);
        review.setReviewerType(reviewerType);
        review.setRating(rating);
        review.setContent(content);
        review.setTags(tags);
        reviewMapper.insert(review);

        // Update runner average rating (reviewerType=1 means student reviewing runner)
        if (reviewerType == 1 && order.getRunnerId() != null) {
            recalcRunnerRating(order.getRunnerId());
        }

        return Result.success(review);
    }

    /**
     * Check if an order has been reviewed by a specific reviewer type.
     * GET /api/reviews/order/{orderId}?reviewerType=1
     */
    @GetMapping("/order/{orderId}")
    public Result getByOrder(@PathVariable Long orderId,
                             @RequestParam(required = false) Integer reviewerType,
                             Authentication authentication) {
        AuthenticatedUser user = SecurityUtils.current(authentication);
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        boolean canView = SecurityUtils.isAdmin(user)
                || ("STUDENT".equals(user.role()) && user.userId().equals(order.getStudentId()))
                || ("RUNNER".equals(user.role()) && user.userId().equals(order.getRunnerId()));
        if (!canView) {
            throw new BusinessException(403, "无权查看该评价");
        }
        if (!SecurityUtils.isAdmin(user)) {
            reviewerType = "STUDENT".equals(user.role()) ? 1 : 2;
        }
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<Review>()
                .eq(Review::getOrderId, orderId);
        if (reviewerType != null) {
            wrapper.eq(Review::getReviewerType, reviewerType);
        }
        List<Review> reviews = reviewMapper.selectList(wrapper);
        return Result.success(reviews.isEmpty() ? null : reviews.get(0));
    }

    /**
     * Recalculate and update a runner's average rating.
     */
    private void recalcRunnerRating(Long runnerId) {
        // Get all order IDs completed by this runner
        List<Long> orderIds = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getRunnerId, runnerId)
                        .eq(Order::getStatus, 5)
                        .select(Order::getId)
        ).stream().map(Order::getId).toList();

        if (orderIds.isEmpty()) return;

        // Average rating from student reviews (reviewerType=1)
        List<Review> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>()
                        .in(Review::getOrderId, orderIds)
                        .eq(Review::getReviewerType, 1));

        if (reviews.isEmpty()) return;

        BigDecimal avg = reviews.stream()
                .map(r -> BigDecimal.valueOf(r.getRating()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(reviews.size()), 1, RoundingMode.HALF_UP);

        Runner runner = runnerMapper.selectById(runnerId);
        if (runner != null) {
            runner.setRating(avg);
            runnerMapper.updateById(runner);
        }
    }
}
