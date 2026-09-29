package com.campus.helper.event;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the two properties {@link OutboxWakeupListener} is responsible for.
 *
 * <p>Delivery must be handed off rather than run inline: a deliver pass blocks on a Kafka send
 * for up to ten seconds per row, and the caller here is a request thread that has already
 * committed — it must not inherit that wait.
 *
 * <p>A rejected hand-off must not escape. The listener runs inside an {@code AFTER_COMMIT}
 * callback on the caller's stack, so an exception thrown here surfaces as a failed request even
 * though the business transaction already committed. The poll interval still covers delivery, so
 * losing the nudge costs latency and nothing else.
 */
class OutboxWakeupListenerTest {

    private final OutboxDeliveryScheduler deliveryScheduler = mock(OutboxDeliveryScheduler.class);
    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);

    private final OutboxWakeupListener listener =
            new OutboxWakeupListener(deliveryScheduler, taskScheduler);

    @Test
    void schedulesADeliveryPassInsteadOfRunningItOnTheCaller() {
        listener.onOutboxWritten(new OutboxWakeupEvent());

        ArgumentCaptor<Runnable> pass = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(pass.capture(), any(Instant.class));

        pass.getValue().run();
        verify(deliveryScheduler).deliver();
    }

    @Test
    void swallowsRejectionSoACommittedRequestStillSucceeds() {
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenThrow(new TaskRejectedException("scheduler pool exhausted"));

        assertThatCode(() -> listener.onOutboxWritten(new OutboxWakeupEvent()))
                .doesNotThrowAnyException();
    }
}
