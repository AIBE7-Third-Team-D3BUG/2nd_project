package org.example._nd_project;

import org.example._nd_project.Controller.TaskProgressController;
import org.example._nd_project.security.MemberPrincipal;
import org.example._nd_project.submission.SubmissionService;
import org.example._nd_project.submission.TaskCompletionService;
import org.example._nd_project.submission.TaskProgressService;
import org.example._nd_project.submission.TaskProgressView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(TaskProgressController.class)
class TaskProgressControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean TaskProgressService taskProgressService;
    @MockitoBean SubmissionService submissionService;
    @MockitoBean TaskCompletionService taskCompletionService;

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
}
