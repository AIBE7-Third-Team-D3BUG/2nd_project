package org.example._nd_project.Controller;

import org.example._nd_project.chat.ChatMessageView;
import org.example._nd_project.chat.ChatService;
import org.example._nd_project.security.MemberPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import tools.jackson.databind.ObjectMapper;

import java.security.Principal;
import java.nio.charset.StandardCharsets;

@Controller
@Profile("db")
public class ChatWebSocketController {
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public ChatWebSocketController(ChatService chatService, SimpMessagingTemplate messagingTemplate,
                                   ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @MessageMapping("/chat/{roomId}/send")
    public void send(@DestinationVariable Long roomId, Message<?> inboundMessage,
                     @Header("simpUser") Principal principal) throws Exception {
        if (!(principal instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken authentication)
                || !(authentication.getPrincipal() instanceof MemberPrincipal memberPrincipal)) {
            throw new IllegalStateException("인증된 사용자만 메시지를 보낼 수 있습니다.");
        }
        String rawPayload = inboundMessage.getPayload() instanceof byte[] bytes
                ? new String(bytes, StandardCharsets.UTF_8)
                : String.valueOf(inboundMessage.getPayload());
        ChatPayload payload = objectMapper.readValue(rawPayload, ChatPayload.class);
        ChatMessageView message = chatService.sendTextMessage(roomId, memberPrincipal.memberId(),
                payload == null ? "" : payload.content());
        messagingTemplate.convertAndSend("/topic/chat." + roomId, message);
    }

    public record ChatPayload(String content) {}
}
