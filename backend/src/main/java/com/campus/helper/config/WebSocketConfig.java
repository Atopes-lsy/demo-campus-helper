package com.campus.helper.config;

import com.campus.helper.security.AuthenticatedUser;
import com.campus.helper.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.MessagingException;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket, served by the in-memory simple broker.
 *
 * <p>The simple broker keeps its subscriptions in this JVM, so a message reaches only the
 * clients connected to the instance that produced it. Running more than one backend replica
 * needs a broker relay (RabbitMQ, ActiveMQ) in place of {@code enableSimpleBroker} — until
 * then, scale the backend to a single instance.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** Heartbeat interval in milliseconds, negotiated with the client on CONNECT. */
    private static final long HEARTBEAT_INTERVAL_MS = 10_000L;

    private final JwtService jwtService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS})
                .setTaskScheduler(heartbeatScheduler());
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * The simple broker needs a scheduler to emit heartbeats.
     *
     * <p>This one is deliberately private rather than an exposed bean. Spring Boot's
     * {@code TaskSchedulingAutoConfiguration} backs off on {@code @ConditionalOnMissingBean
     * (TaskScheduler.class)}, so publishing this scheduler as a bean would replace the one
     * every {@code @Scheduled} method shares — and since outbox delivery blocks on a Kafka
     * send for up to ten seconds per row, heartbeats would end up queued behind it.
     */
    private TaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                        message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authorization = accessor.getFirstNativeHeader("Authorization");
                    if (authorization == null || !authorization.startsWith("Bearer ")) {
                        throw new MessagingException("Missing access token");
                    }
                    try {
                        AuthenticatedUser user = jwtService.verify(authorization.substring(7));
                        accessor.setUser(() -> user.userId() + "_" + user.role());
                    } catch (IllegalArgumentException e) {
                        throw new MessagingException("Invalid access token");
                    }
                }
                return message;
            }
        });
    }
}
