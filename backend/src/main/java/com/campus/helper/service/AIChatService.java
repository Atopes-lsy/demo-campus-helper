package com.campus.helper.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.helper.ai.DeepSeekClient;
import com.campus.helper.ai.PromptTemplates;
import com.campus.helper.entity.AIChatHistory;
import com.campus.helper.mapper.AIChatHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIChatService {

    private final DeepSeekClient deepSeekClient;
    private final AIChatHistoryMapper aiChatHistoryMapper;

    /**
     * Chat with the AI assistant, maintaining context from the conversation history.
     * Loads the last 10 message pairs (20 rows) from the same session as context.
     *
     * @param userId    the user's ID
     * @param userType  the user type ("STUDENT" or "RUNNER")
     * @param sessionId the chat session identifier
     * @param message   the user's message
     * @return the AI assistant's reply
     */
    public String chat(Long userId, String userType, String sessionId, String message) {
        // Save user message to history
        AIChatHistory userMsg = new AIChatHistory();
        userMsg.setSessionId(sessionId);
        userMsg.setUserId(userId);
        userMsg.setUserType(userType);
        userMsg.setRole("user");
        userMsg.setContent(message);
        userMsg.setCreatedAt(LocalDateTime.now());
        aiChatHistoryMapper.insert(userMsg);

        // Load last 10 messages (20 rows: user + assistant) from same session
        List<AIChatHistory> recentHistory = aiChatHistoryMapper.selectList(
                new LambdaQueryWrapper<AIChatHistory>()
                        .eq(AIChatHistory::getSessionId, sessionId)
                        .orderByDesc(AIChatHistory::getId)
                        .last("LIMIT 20")
        );
        // Reverse to chronological order for context building
        Collections.reverse(recentHistory);

        // Build context string from history + current message
        StringBuilder contextBuilder = new StringBuilder();
        for (AIChatHistory h : recentHistory) {
            contextBuilder.append(h.getRole()).append(": ").append(h.getContent()).append("\n");
        }
        String context = contextBuilder.toString();
        log.debug("Chat context for session {}: {} characters from {} history rows",
                sessionId, context.length(), recentHistory.size());

        // Call DeepSeek AI
        String reply = deepSeekClient.chat(PromptTemplates.CUSTOMER_SERVICE, context, 0.7);

        // Save assistant response to history
        AIChatHistory assistantMsg = new AIChatHistory();
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setUserId(userId);
        assistantMsg.setUserType(userType);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(reply);
        assistantMsg.setCreatedAt(LocalDateTime.now());
        aiChatHistoryMapper.insert(assistantMsg);

        log.info("Chat completed for session {}: user={}, reply length={}", sessionId, userId, reply.length());
        return reply;
    }
}
