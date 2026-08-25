package org.example._nd_project.Controller;

import org.example._nd_project.chat.ChatRoomView;
import org.example._nd_project.chat.ChatService;
import org.example._nd_project.security.MemberPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.context.annotation.Profile;
import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@Profile("db")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/chat")
    public String chat(@AuthenticationPrincipal MemberPrincipal principal,
                       @RequestParam(value = "room", required = false) Long roomId,
                       Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        chatService.ensureDemoRoomFor(principal);
        List<ChatRoomView> rooms = chatService.getRoomsFor(principal);
        ChatRoomView selectedRoom = rooms.isEmpty()
                ? null
                : rooms.stream().filter(room -> room.id().equals(roomId)).findFirst().orElse(rooms.get(0));
        model.addAttribute("chatRooms", rooms);
        model.addAttribute("selectedRoom", selectedRoom);
        model.addAttribute("currentMemberId", principal.memberId());
        return "chat";
    }
}
