package org.example._nd_project;

import org.example._nd_project.Controller.TaskController;
import org.example._nd_project.security.MemberPrincipal;
import org.example._nd_project.task.TaskService;
import org.example._nd_project.volunteer.VolunteerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean TaskService taskService;
    @MockitoBean VolunteerService volunteerService;

    @Test
    void restrictedApplicationRedirectsWithUserFacingReason() throws Exception {
        MemberPrincipal principal = new MemberPrincipal(
                3L, "member@example.com", "password", "회원", "USER", true);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,
                "최근 90일 지연 점수가 5점으로 신규 업무 지원이 제한됩니다."))
                .when(volunteerService).apply(10L, 3L, null);

        mockMvc.perform(post("/tasks/10/apply").with(csrf()).with(user(principal)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?view=detail&taskId=10"))
                .andExpect(flash().attribute("applyError",
                        "최근 90일 지연 점수가 5점으로 신규 업무 지원이 제한됩니다."));
    }
}
