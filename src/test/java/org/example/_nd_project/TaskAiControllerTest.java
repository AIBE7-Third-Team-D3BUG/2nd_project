package org.example._nd_project;

import org.example._nd_project.Controller.TaskAiController;
import org.example._nd_project.security.MemberPrincipal;
import org.example._nd_project.task.TaskCategory;
import org.example._nd_project.task.ai.AiTaskDraft;
import org.example._nd_project.task.ai.TaskAiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskAiController.class)
class TaskAiControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean TaskAiService taskAiService;

    @Test
    void authenticatedMemberCanRequestDraft() throws Exception {
        MemberPrincipal principal = new MemberPrincipal(
                3L, "member@example.com", "password", "회원", "USER", true
        );
        when(taskAiService.createDraft(anyString())).thenReturn(new AiTaskDraft(
                "AWS 배포 후 502 오류 해결",
                TaskCategory.DEVELOPMENT,
                List.of("AWS", "Nginx", "Spring Boot"),
                "서비스 URL 정상 접속 확인",
                "배포 환경과 로그를 확인합니다."
        ));

        mockMvc.perform(post("/tasks/ai/draft")
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"situation":"오늘 AWS에 배포했는데 502 오류가 발생합니다."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("AWS 배포 후 502 오류 해결"))
                .andExpect(jsonPath("$.category").value("DEVELOPMENT"))
                .andExpect(jsonPath("$.requiredSkills[0]").value("AWS"));

        verify(taskAiService).createDraft("오늘 AWS에 배포했는데 502 오류가 발생합니다.");
    }

    @Test
    void csrfTokenIsRequired() throws Exception {
        MemberPrincipal principal = new MemberPrincipal(
                3L, "member@example.com", "password", "회원", "USER", true
        );

        mockMvc.perform(post("/tasks/ai/draft")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"situation\":\"업무를 정리해주세요.\"}"))
                .andExpect(status().isForbidden());
    }
}
