package org.example._nd_project;

import org.example._nd_project.Controller.WorkerRecommendationController;
import org.example._nd_project.security.MemberPrincipal;
import org.example._nd_project.volunteer.WorkerRecommendationService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkerRecommendationController.class)
class WorkerRecommendationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean WorkerRecommendationService recommendationService;

    @Test
    void requesterCanAnalyzeApplicants() throws Exception {
        MemberPrincipal principal = new MemberPrincipal(
                3L, "member@example.com", "password", "회원", "USER", true
        );
        when(recommendationService.recommend(10L, 3L)).thenReturn(List.of());

        mockMvc.perform(post("/tasks/10/worker-recommendations")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?view=compare&taskId=10"))
                .andExpect(flash().attribute("recommendationError", "추천할 수 있는 지원자가 없습니다."));

        verify(recommendationService).recommend(10L, 3L);
    }

    @Test
    void csrfTokenIsRequired() throws Exception {
        MemberPrincipal principal = new MemberPrincipal(
                3L, "member@example.com", "password", "회원", "USER", true
        );

        mockMvc.perform(post("/tasks/10/worker-recommendations").with(user(principal)))
                .andExpect(status().isForbidden());
    }
}
