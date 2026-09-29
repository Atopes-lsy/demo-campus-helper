package com.campus.helper.service;

import com.campus.helper.entity.Order;
import com.campus.helper.entity.OrderTracking;
import com.campus.helper.entity.Runner;
import com.campus.helper.event.KafkaEventPublisher;
import com.campus.helper.mapper.OrderMapper;
import com.campus.helper.mapper.OrderTrackingMapper;
import com.campus.helper.mapper.RunnerMapper;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the conditional write at the end of {@link DispatchService#dispatch(Long)}.
 *
 * <p>Dispatch reads a "pending" snapshot of the order, scores every free runner, and only then
 * writes. A runner can grab that same order through {@code OrderService.updateStatus} — a CAS on
 * the same status column — at any point in between. The old unconditional {@code updateById}
 * overwrote {@code runner_id} with whichever runner dispatch had chosen, so the grabbing runner
 * believed it held an order the database had already reassigned: two runners owned one order.
 *
 * <p>These tests fail against that arrangement (the first one sees the write and the side effects
 * fire despite the order having been claimed) and pass once dispatch goes through the same CAS
 * as the grab path — losing it means backing off without touching anything.
 */
class DispatchServiceTest {

    private static final long ORDER_ID = 10L;
    private static final long RUNNER_ID = 7L;

    private final RunnerMapper runnerMapper = mock(RunnerMapper.class);
    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final OrderTrackingMapper trackingMapper = mock(OrderTrackingMapper.class);
    private final PricingRuleService pricingRuleService = mock(PricingRuleService.class);
    private final RedissonClient redissonClient = mock(RedissonClient.class);
    private final KafkaEventPublisher publisher = mock(KafkaEventPublisher.class);

    private final DispatchService service = new DispatchService(
            runnerMapper, orderMapper, trackingMapper, pricingRuleService,
            redissonClient, publisher);

    @Test
    void backsOffWithoutSideEffectsWhenRunnerGrabbedTheOrderFirst() throws Exception {
        givenPendingOrderWithOneFreeRunner();
        // The grab path won the race between our read and our write.
        when(orderMapper.updateStatusIfCurrent(ORDER_ID, 0, 1, RUNNER_ID, null)).thenReturn(0);

        Runner assigned = service.dispatch(ORDER_ID);

        assertThat(assigned)
                .as("CAS 失败时不得返回跑腿员：订单已归抢单者所有")
                .isNull();
        verify(runnerMapper, never()).updateById(any(Runner.class));
        verifyNoInteractions(trackingMapper, publisher);
    }

    @Test
    void claimsOrderThroughCasAndReportsTheAssignedRunner() throws Exception {
        givenPendingOrderWithOneFreeRunner();
        when(orderMapper.updateStatusIfCurrent(ORDER_ID, 0, 1, RUNNER_ID, null)).thenReturn(1);

        Runner assigned = service.dispatch(ORDER_ID);

        assertThat(assigned).isNotNull();
        assertThat(assigned.getId()).isEqualTo(RUNNER_ID);
        verify(runnerMapper).updateById(any(Runner.class));
        verify(trackingMapper).insert(any(OrderTracking.class));
        verify(publisher).publish(any(Order.class), eq("ORDER_DISPATCHED"), eq(0), eq(1), anyString());
    }

    /**
     * Order read as pending (status 0), one free runner whose score clears the threshold.
     * The order carries no coordinates, so the distance factor falls back to its neutral 0.5
     * and the single runner scores 0.65 — comfortably above the 0.3 default threshold.
     */
    private void givenPendingOrderWithOneFreeRunner() throws Exception {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setStatus(0);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);

        Runner runner = new Runner();
        runner.setId(RUNNER_ID);
        runner.setStatus(0);
        when(runnerMapper.selectList(any())).thenReturn(List.of(runner));
        when(orderMapper.selectActiveOrderCountByRunner()).thenReturn(List.of());
        when(pricingRuleService.activeRules()).thenReturn(List.of());

        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("dispatch:order:" + ORDER_ID)).thenReturn(lock);
        when(lock.tryLock(3, 10, TimeUnit.SECONDS)).thenReturn(true);
    }
}
