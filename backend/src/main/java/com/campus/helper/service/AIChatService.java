package com.campus.helper.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AI chat service — ADMIN-only access.
 * Student and runner AI functionality has been removed.
 * All admin AI logic is delegated to {@link AdminAIService}.
 */
@Service
@RequiredArgsConstructor
public class AIChatService {

    private final AdminAIService adminAIService;

    /**
     * Chat entry point. Only ADMIN users are served; others receive a rejection message.
     *
     * @param userId      the user's ID
     * @param userType    the user type — must be "ADMIN"
     * @param sessionId   the chat session identifier
     * @param message     the user's message
     * @param temperature AI temperature (0.1~0.9), default 0.5
     * @param style       response style: "precise" | "concise" | "detailed", default "detailed"
     * @return the AI assistant's reply, or an access-denied message
     */
    public String chat(Long userId, String userType, String sessionId, String message,
                       double temperature, String style) {
        if (!"ADMIN".equalsIgnoreCase(userType)) {
            return "AI 助手仅对管理员开放。如有问题请联系管理员处理。";
        }

        double temp = Math.max(0.1, Math.min(0.9, temperature));
        String actualStyle = (style != null && !style.isEmpty()) ? style : "detailed";

        return adminAIService.adminChat(userId, sessionId, message, temp, actualStyle);
    }
}
