package com.campus.helper.service;

import com.campus.helper.entity.Notification;
import com.campus.helper.entity.Order;
import com.campus.helper.entity.Payment;
import com.campus.helper.entity.Runner;
import com.campus.helper.mapper.NotificationMapper;
import com.campus.helper.websocket.NotificationHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final NotificationHandler notificationHandler;

    /**
     * Send dispatch notifications to both the student and the assigned runner.
     * Saves to DB and pushes in real-time via WebSocket.
     */
    @Transactional
    public void sendDispatchNotification(Order order, Runner runner) {
        // Notify student
        Notification studentNotif = buildNotification(
                order.getStudentId(),
                "STUDENT",
                2,
                "派单成功",
                "已匹配跑腿员" + runner.getName() + "(评分" + runner.getRating() + ")",
                order.getId()
        );
        notificationMapper.insert(studentNotif);
        notificationHandler.sendToUser(
                String.valueOf(order.getStudentId()),
                "STUDENT",
                "/queue/notifications",
                studentNotif
        );

        // Notify runner
        Notification runnerNotif = buildNotification(
                runner.getId(),
                "RUNNER",
                2,
                "新订单",
                "订单" + order.getTitle() + ", ¥" + order.getTip(),
                order.getId()
        );
        notificationMapper.insert(runnerNotif);
        notificationHandler.sendToUser(
                String.valueOf(runner.getId()),
                "RUNNER",
                "/queue/notifications",
                runnerNotif
        );

        log.info("Dispatch notifications sent for order {}", order.getId());
    }

    /**
     * Send status-change notifications to the student and (if assigned) the runner.
     */
    @Transactional
    public void sendStatusChange(Order order, Integer newStatus) {
        String statusDesc = getStatusDescription(newStatus);

        // Notify student
        Notification studentNotif = buildNotification(
                order.getStudentId(),
                "STUDENT",
                1,
                "订单状态更新",
                "您的订单" + order.getOrderNo() + " " + statusDesc,
                order.getId()
        );
        notificationMapper.insert(studentNotif);
        notificationHandler.sendToUser(
                String.valueOf(order.getStudentId()),
                "STUDENT",
                "/queue/notifications",
                studentNotif
        );

        // Notify runner if assigned
        if (order.getRunnerId() != null) {
            Notification runnerNotif = buildNotification(
                    order.getRunnerId(),
                    "RUNNER",
                    1,
                    "订单状态更新",
                    "订单" + order.getOrderNo() + " " + statusDesc,
                    order.getId()
            );
            notificationMapper.insert(runnerNotif);
            notificationHandler.sendToUser(
                    String.valueOf(order.getRunnerId()),
                    "RUNNER",
                    "/queue/notifications",
                    runnerNotif
            );
        }

        log.info("Status-change notifications sent for order {} (newStatus={})", order.getId(), newStatus);
    }

    /**
     * Send a payment-related notification to the payer.
     */
    @Transactional
    public void sendPaymentNotification(Payment payment, String status) {
        Notification notif = buildNotification(
                payment.getPayerId(),
                "STUDENT",
                3,
                "支付" + status,
                "订单支付" + status + ", 金额¥" + payment.getAmount(),
                payment.getOrderId()
        );
        notificationMapper.insert(notif);
        notificationHandler.sendToUser(
                String.valueOf(payment.getPayerId()),
                "STUDENT",
                "/queue/notifications",
                notif
        );

        log.info("Payment notification sent for order {} (paymentId={}, status={})",
                payment.getOrderId(), payment.getId(), status);
    }

    /**
     * Build a Notification entity with common fields populated.
     */
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

    /**
     * Map a numeric status code to a human-readable Chinese description.
     */
    private String getStatusDescription(Integer status) {
        switch (status) {
            case 0:
                return "已提交";
            case 1:
                return "跑腿员已接单";
            case 2:
                return "跑腿员前往取件";
            case 3:
                return "已取件配送中";
            case 4:
                return "已送达";
            case 5:
                return "学生确认收货";
            case 6:
                return "订单已取消";
            default:
                return "状态更新";
        }
    }
}
