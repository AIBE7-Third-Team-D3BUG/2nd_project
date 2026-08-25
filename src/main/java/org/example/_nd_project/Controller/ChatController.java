package org.example._nd_project.Controller;

import org.example._nd_project.chat.ChatRoomView;
import org.example._nd_project.chat.ChatService;
import org.example._nd_project.security.MemberPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/chat")
    public String chat(@AuthenticationPrincipal MemberPrincipal principal,
                       @RequestParam(name = "room", required = false) Long selectedRoomId,
                       Model model) {
        List<ChatRoomView> rooms = chatService.getRooms(principal.memberId());
        Long roomId = selectedRoomId != null
                ? selectedRoomId
                : rooms.stream().findFirst().map(ChatRoomView::id).orElse(null);
        ChatRoomView selectedRoom = roomId == null ? null : chatService.openRoom(roomId, principal.memberId());
        if (selectedRoom != null) {
            rooms = chatService.getRooms(principal.memberId());
        }
        model.addAttribute("chatRooms", rooms);
        model.addAttribute("selectedRoom", selectedRoom);
        model.addAttribute("currentMemberId", principal.memberId());
        return "chat";
    }

    @GetMapping("/tasks/{taskId}/chat")
    public String taskChat(@AuthenticationPrincipal MemberPrincipal principal,
                           @PathVariable Long taskId) {
        Long roomId = chatService.findRoomIdForTask(taskId, principal.memberId());
        return "redirect:/chat?room=" + roomId;
    }
}
