package com.campus.helper.service;

import com.campus.helper.common.BusinessException;
import com.campus.helper.entity.Order;
import com.campus.helper.entity.Runner;
import com.campus.helper.event.KafkaEventPublisher;
import com.campus.helper.mapper.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceConcurrencyTest {

    @Test
    void staleClaimIsRejectedWithoutTrackingOrRunnerMutation() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        OrderTrackingMapper trackingMapper = mock(OrderTrackingMapper.class);
        StudentMapper studentMapper = mock(StudentMapper.class);
        RunnerMapper runnerMapper = mock(RunnerMapper.class);
        PaymentMapper paymentMapper = mock(PaymentMapper.class);
        KafkaEventPublisher publisher = mock(KafkaEventPublisher.class);
        OrderService service = new OrderService(orderMapper, trackingMapper, studentMapper,
                runnerMapper, paymentMapper, publisher);

        Order order = new Order();
        order.setId(10L);
        order.setStatus(0);
        Runner runner = new Runner();
        runner.setId(7L);
        runner.setStatus(0);
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(runnerMapper.selectById(7L)).thenReturn(runner);
        when(orderMapper.updateStatusIfCurrent(10L, 0, 1, 7L, null)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateStatus(10L, 1, 7L, "RUNNER"));

        assertEquals(409, exception.getCode());
        verifyNoInteractions(trackingMapper, publisher);
        verify(runnerMapper, never()).updateById(any(Runner.class));
    }
}
