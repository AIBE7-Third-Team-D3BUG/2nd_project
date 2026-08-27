package org.example._nd_project;

import org.example._nd_project.Controller.TaskProgressController;
import org.example._nd_project.security.MemberPrincipal;
import org.example._nd_project.submission.SubmissionService;
import org.example._nd_project.submission.ReviewForm;
import org.example._nd_project.submission.TaskCompletionService;
import org.example._nd_project.submission.TaskProgressService;
import org.example._nd_project.submission.TaskProgressView;
import org.example._nd_project.submission.TaskWorkflowService;
import org.example._nd_project.task.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

@WebMvcTest(TaskProgressController.class)
class TaskProgressControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean TaskProgressService taskProgressService;
    @MockitoBean SubmissionService submissionService;
    @MockitoBean TaskCompletionService taskCompletionService;
    @MockitoBean TaskWorkflowService taskWorkflowService;
    @MockitoBean TaskService taskService;

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
                ),
                null
        );
        when(taskProgressService.getProgress(10L, 3L)).thenReturn(progress);

        mockMvc.perform(get("/tasks/10/progress").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("task-progress"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CLIENT-04 · 완료 승인 / 리뷰")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("결과가 도착했어요")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("작업자 평점")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("후기 작성 및 완료 승인")));
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
    void requesterCanCancelInProgressTask() throws Exception {
        MemberPrincipal requester = new MemberPrincipal(
                3L, "requester@example.com", "password", "의뢰자", "USER", true
        );

        mockMvc.perform(post("/tasks/10/cancel").with(user(requester)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile?cancelledTask"));

        verify(taskService).cancelActiveTask(10L, 3L);
    }

    @Test
    void oversizedResultFileReturnsToSubmissionScreenWithMessage() throws Exception {
        MemberPrincipal worker = new MemberPrincipal(
                8L, "worker@example.com", "password", "작업자", "USER", true
        );
        doThrow(new MultipartException("request too large"))
                .when(submissionService)
                .submit(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.eq(8L), any(), any());

        mockMvc.perform(post("/tasks/10/submissions")
                        .with(user(worker))
                        .with(csrf())
                        .param("resultDescription", "결과를 제출합니다."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tasks/10/progress"))
                .andExpect(flash().attribute("uploadError",
                        "첨부 파일 용량이 너무 커서 전송할 수 없습니다. 6MB 이하 파일을 선택해주세요."));
    }

    @Test
    void deletedTaskProgressPageRedirectsToProfileWithMessage() throws Exception {
        MemberPrincipal worker = new MemberPrincipal(
                8L, "worker@example.com", "password", "작업자", "USER", true
        );
        when(taskProgressService.getProgress(10L, 8L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/tasks/10/progress").with(user(worker)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("taskDeletedMessage",
                        "의뢰자가 글을 삭제했습니다. 내 업무 목록으로 이동했습니다."));
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
                false,
                2,
                List.of("서비스 정상 응답 확인"),
                List.of(),
                null,
                null
        );
        when(taskProgressService.getProgress(10L, 8L)).thenReturn(progress);

        mockMvc.perform(get("/tasks/10/progress").with(user(worker)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("COMMON-03 · 업무 시작")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("작업자로 선택되었어요")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("업무 시작하기")));
    }

    @Test
    void requesterApprovalSubmitsReview() throws Exception {
        MemberPrincipal requester = new MemberPrincipal(
                3L,
                "requester@example.com",
                "password",
                "의뢰인",
                "USER",
                true
        );

        mockMvc.perform(post("/tasks/10/approve")
                        .with(user(requester))
                        .with(csrf())
                        .param("rating", "5")
                        .param("content", "빠르게 해결해주셨어요.")
                        .param("deadlineMet", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tasks/10/progress?approved"));

        verify(taskCompletionService).approve(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(3L),
                any(ReviewForm.class)
        );
    }

    @Test
    void approvalWithoutRatingDoesNotCompleteTask() throws Exception {
        MemberPrincipal requester = new MemberPrincipal(
                3L,
                "requester@example.com",
                "password",
                "의뢰인",
                "USER",
                true
        );

        mockMvc.perform(post("/tasks/10/approve")
                        .with(user(requester))
                        .with(csrf())
                        .param("content", "평점 없이 보낸 후기"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tasks/10/progress"));

        verify(taskCompletionService, never()).approve(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                any(ReviewForm.class)
        );
    }
}
