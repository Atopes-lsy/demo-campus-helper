package com.campus.helper.event.consumer;

import com.campus.helper.entity.Notification;
import com.campus.helper.event.KafkaConfig;
import com.campus.helper.event.OrderEvent;
import com.campus.helper.event.EventDeduplicator;
import com.campus.helper.mapper.NotificationMapper;
import com.campus.helper.websocket.NotificationHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes order events and dispatches real-time notifications
 * to students and runners via WebSocket + DB persistence.
 *
 * Runs in its own consumer group to receive ALL events independently.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationMapper notificationMapper;
    private final NotificationHandler notificationHandler;
    private final EventDeduplicator eventDeduplicator;

    @KafkaListener(
            topics = KafkaConfig.ORDER_EVENTS_TOPIC,
            groupId = "campus-notification-group",
            concurrency = "2"
    )
    @Transactional
    public void onOrderEvent(OrderEvent event) {
        if (!eventDeduplicator.firstDelivery(event.getEventId(), "notification")) {
            return;
        }
        log.info("[通知消费者] 收到事件: orderId={}, type={}, {} → {}",
                event.getOrderId(), event.getEventType(),
                event.getOldStatus(), event.getNewStatus());

        switch (event.getEventType()) {
            case "ORDER_CREATED" -> notifyStudent(event, "订单已提交",
                    "您的订单 " + event.getOrderNo() + " 已提交，等待跑腿员接单");

            case "ORDER_DISPATCHED" -> {
                notifyStudent(event, "派单成功",
                        "订单 " + event.getOrderNo() + " 已匹配跑腿员");
                if (event.getRunnerId() != null) {
                    notifyRunner(event, "新订单",
                            "您有新订单: " + event.getTitle() + ", ¥" + event.getAmount());
                }
            }

            case "ORDER_ACCEPTED" -> notifyStudent(event, "跑腿员已接单",
                    "跑腿员已接单，订单 " + event.getOrderNo() + " 正在处理中");

            case "ORDER_PICKED_UP" -> notifyStudent(event, "已取件",
                    "跑腿员已取件，订单 " + event.getOrderNo() + " 配送中");

            case "ORDER_IN_TRANSIT" -> notifyStudent(event, "配送中",
                    "订单 " + event.getOrderNo() + " 正在配送中");

            case "ORDER_DELIVERED" -> notifyStudent(event, "已送达",
                    "订单 " + event.getOrderNo() + " 已送达，请确认收货");

            case "ORDER_COMPLETED" -> {
                notifyStudent(event, "已完成", "订单 " + event.getOrderNo() + " 已完成");
                if (event.getRunnerId() != null) {
                    notifyRunner(event, "订单完成",
                            "订单 " + event.getOrderNo() + " 已完成，收入 ¥" + event.getAmount());
                }
            }

            case "ORDER_CANCELLED" -> {
                notifyStudent(event, "已取消", "订单 " + event.getOrderNo() + " 已取消");
                if (event.getRunnerId() != null) {
                    notifyRunner(event, "订单取消",
                            "订单 " + event.getOrderNo() + " 已被取消");
                }
            }
        }
    }

    private void notifyStudent(OrderEvent event, String title, String content) {
        Notification notif = buildNotification(event.getStudentId(), "STUDENT", 1,
                title, content, event.getOrderId());
        notificationMapper.insert(notif);
        notificationHandler.sendToUser(String.valueOf(event.getStudentId()), "STUDENT",
                "/queue/notifications", notif);
    }

    private void notifyRunner(OrderEvent event, String title, String content) {
        Notification notif = buildNotification(event.getRunnerId(), "RUNNER", 2,
                title, content, event.getOrderId());
        notificationMapper.insert(notif);
        notificationHandler.sendToUser(String.valueOf(event.getRunnerId()), "RUNNER",
                "/queue/notifications", notif);
    }

    private Notification buildNotification(Long userId, String userType, Integer notifyType,
                                           String title, String content, Long relatedOrderId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setUserType(userType);
        notification.setNotifyType(notifyType);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRelatedOrderId(relatedOrderId);
        notification.setIsRead(0);
        return notification;
    }
}
