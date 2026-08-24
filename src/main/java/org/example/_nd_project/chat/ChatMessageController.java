package org.example._nd_project.chat;

import org.example._nd_project.security.MemberPrincipal;
import org.springframework.core.io.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;

@Controller
public class ChatMessageController {

    private final ChatService chatService;

    public ChatMessageController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat/{roomId}/messages")
    public String sendMessage(@PathVariable Long roomId,
                              @RequestParam(required = false) String content,
                              @RequestParam(required = false) MultipartFile attachment,
                              @AuthenticationPrincipal MemberPrincipal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        if ((content != null && !content.isBlank()) || (attachment != null && !attachment.isEmpty())) {
            chatService.saveMessage(roomId, principal.memberId(), content, attachment);
        }
        return "redirect:/chat?room=" + roomId;
    }

    @GetMapping("/chat/messages/{messageId}/attachment")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long messageId,
                                                       @RequestParam(defaultValue = "false") boolean download,
                                                       @AuthenticationPrincipal MemberPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/login")
                    .build();
        }

        ChatService.AttachmentDownload attachment = chatService.getAttachmentDownload(messageId, principal.memberId());
        MediaType mediaType = MediaType.parseMediaType(attachment.contentType());
        ContentDisposition disposition = !download && attachment.previewableImage()
                ? ContentDisposition.inline().filename(attachment.filename(), StandardCharsets.UTF_8).build()
                : ContentDisposition.attachment().filename(attachment.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(attachment.size() != null ? attachment.size() : 0)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(attachment.resource());
    }
}
