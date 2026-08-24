package org.example._nd_project;

import org.example._nd_project.Controller.TaskProgressController;
import org.example._nd_project.security.MemberPrincipal;
import org.example._nd_project.submission.SubmissionService;
import org.example._nd_project.submission.TaskCompletionService;
import org.example._nd_project.submission.TaskProgressService;
import org.example._nd_project.submission.TaskProgressView;
import org.example._nd_project.submission.TaskWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.mockito.Mockito.verify;

@WebMvcTest(TaskProgressController.class)
class TaskProgressControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean TaskProgressService taskProgressService;
    @MockitoBean SubmissionService submissionService;
    @MockitoBean TaskCompletionService taskCompletionService;
    @MockitoBean TaskWorkflowService taskWorkflowService;

    @Test
    void requesterReviewScreenRendersSubmittedResult() throws Exception {
        MemberPrincipal principal = new MemberPrincipal(
                3L,
                "requester@example.com",
                "password",
                "의뢰인",
                "USER",
                true
        );
        TaskProgressView progress = new TaskProgressView(
                10L,
                "AWS 배포 후 502 오류 해결",
                "의뢰인",
                "작업자",
                "2시간 42분 남음",
                120,
                "결과 확인",
                true,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                4,
                List.of("서비스 정상 응답 확인"),
                List.of(new TaskProgressView.ActivityView(
                        "결과가 제출되었습니다.",
                        "작업자",
                        "8월 24일 14:20",
                        true
                )),
                new TaskProgressView.SubmissionView(
                        "Nginx upstream 설정을 수정했습니다.",
                        true,
                        "결과 링크 열기",
                        "8월 24일 14:20",
                        null
                )
        );
        when(taskProgressService.getProgress(10L, 3L)).thenReturn(progress);

        mockMvc.perform(get("/tasks/10/progress").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("task-progress"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("결과가 도착했어요")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("완료 승인하기")));
    }

    @Test
    void selectedWorkerCanStartTaskFromProgressScreen() throws Exception {
        MemberPrincipal worker = new MemberPrincipal(
                8L,
                "worker@example.com",
                "password",
                "작업자",
                "USER",
                true
        );

        mockMvc.perform(post("/tasks/10/start").with(user(worker)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tasks/10/progress?started"));

        verify(taskWorkflowService).start(10L, 8L);
    }

    @Test
    void matchedWorkerScreenRendersStartAction() throws Exception {
        MemberPrincipal worker = new MemberPrincipal(
                8L,
                "worker@example.com",
                "password",
                "작업자",
                "USER",
                true
        );
        TaskProgressView progress = new TaskProgressView(
                10L,
                "AWS 배포 후 502 오류 해결",
                "의뢰인",
                "작업자",
                "2시간 42분 남음",
                120,
                "매칭 완료",
                false,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                2,
                List.of("서비스 정상 응답 확인"),
                List.of(),
                null
        );
        when(taskProgressService.getProgress(10L, 8L)).thenReturn(progress);

        mockMvc.perform(get("/tasks/10/progress").with(user(worker)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("작업자로 선택되었어요")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("업무 시작하기")));
    }
}
