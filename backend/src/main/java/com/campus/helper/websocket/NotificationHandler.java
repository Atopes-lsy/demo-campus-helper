package com.campus.helper.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotificationHandler {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Send a message to a specific user.
     *
     * @param userId      the user's ID
     * @param userType    the user type (e.g., "student", "runner")
     * @param destination the destination path (e.g., "/queue/notifications")
     * @param payload     the message payload
     */
    public void sendToUser(String userId, String userType, String destination, Object payload) {
        messagingTemplate.convertAndSendToUser(
                userId + "_" + userType,
                destination,
                payload
        );
    }
}
