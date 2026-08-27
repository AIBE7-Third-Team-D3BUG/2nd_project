package org.example._nd_project;

import org.example._nd_project.Controller.ChatController;
import org.example._nd_project.Controller.ChatMessageController;
import org.example._nd_project.chat.ChatRoomView;
import org.example._nd_project.chat.ChatService;
import org.example._nd_project.security.MemberPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest({ChatController.class, ChatMessageController.class})
class ChatControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ChatService chatService;

    private final MemberPrincipal principal = new MemberPrincipal(
            1L, "requester@example.com", "password", "의뢰인", "USER", true);

    @Test
    void chatRoomRendersForParticipant() throws Exception {
        ChatRoomView room = new ChatRoomView(
                3L, 10L, "AWS 배포 오류 해결", 2L, "작업자",
                "대화를 시작해보세요.", "", 0, List.of(), false);
        when(chatService.getRooms(1L)).thenReturn(List.of(room));
        when(chatService.openRoom(3L, 1L)).thenReturn(room);

        mockMvc.perform(get("/chat").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("chat"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("AWS 배포 오류 해결")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("첫 메시지를 보내보세요")));
    }

    @Test
    void emptyChatListRendersForNewMember() throws Exception {
        when(chatService.getRooms(1L)).thenReturn(List.of());

        mockMvc.perform(get("/chat").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("chat"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("아직 시작된 대화가 없어요")));
    }

    @Test
    void messagePostRedirectsBackToRoom() throws Exception {
        mockMvc.perform(multipart("/chat/3/messages")
                        .param("content", "진행 상황을 공유합니다.")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/chat?room=3"));

        verify(chatService).sendMessage(3L, 1L, "진행 상황을 공유합니다.", null);
    }
}
