package org.example._nd_project.Controller;

import org.example._nd_project.chat.ChatService;
import org.example._nd_project.security.MemberPrincipal;
import org.example._nd_project.task.TaskStorageException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@Profile("db")
public class ChatMessageController {

    private final ChatService chatService;

    public ChatMessageController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat/{roomId}/messages")
    public String send(@AuthenticationPrincipal MemberPrincipal principal,
                       @PathVariable Long roomId,
                       @RequestParam(name = "content", defaultValue = "") String content,
                       @RequestParam(name = "attachment", required = false) MultipartFile attachment,
                       RedirectAttributes redirectAttributes) {
        try {
            chatService.sendMessage(roomId, principal.memberId(), content, attachment);
        } catch (IllegalArgumentException | TaskStorageException exception) {
            redirectAttributes.addFlashAttribute("chatError", exception.getMessage());
        }
        return "redirect:/chat?room=" + roomId;
    }

    @GetMapping("/chat/messages/{messageId}/attachment")
    public RedirectView attachment(@AuthenticationPrincipal MemberPrincipal principal,
                                   @PathVariable Long messageId) {
        return new RedirectView(chatService.createAttachmentDownloadUrl(messageId, principal.memberId()).toString());
    }
}
