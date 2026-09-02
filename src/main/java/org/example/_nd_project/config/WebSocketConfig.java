package org.example._nd_project.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.example._nd_project.chat.ChatRoomRepository;
import org.example._nd_project.security.MemberPrincipal;
import org.springframework.security.access.AccessDeniedException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
@Profile("db")
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private static final Pattern CHAT_TOPIC = Pattern.compile("^/topic/chat\\.(\\d+)$");
    private final ChatRoomRepository chatRoomRepository;

    public WebSocketConfig(ChatRoomRepository chatRoomRepository) {
        this.chatRoomRepository = chatRoomRepository;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
    }

    @Override
    public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    authorizeChatSubscription(accessor);
                }
                return message;
            }
        });
    }

    private void authorizeChatSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        Matcher matcher = destination == null ? null : CHAT_TOPIC.matcher(destination);
        if (matcher == null || !matcher.matches()) {
            return;
        }

        if (!(accessor.getUser() instanceof UsernamePasswordAuthenticationToken authentication)
                || !(authentication.getPrincipal() instanceof MemberPrincipal principal)) {
            throw new AccessDeniedException("인증된 사용자만 채팅방을 구독할 수 있습니다.");
        }

        Long roomId = Long.valueOf(matcher.group(1));
        boolean isMember = chatRoomRepository.findById(roomId)
                .map(room -> room.hasMember(principal.memberId()))
                .orElse(false);
        if (!isMember) {
            throw new AccessDeniedException("채팅방 참여자만 구독할 수 있습니다.");
        }
    }
}
