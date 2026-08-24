package org.example._nd_project.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChatSendRequest {

    @NotBlank
    @Size(max = 2000)
    private String content;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
