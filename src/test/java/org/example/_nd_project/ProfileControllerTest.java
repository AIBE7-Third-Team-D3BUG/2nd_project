package org.example._nd_project;

import org.example._nd_project.Controller.ProfileController;
import org.example._nd_project.member.MemberProfileView;
import org.example._nd_project.member.MemberService;
import org.example._nd_project.member.MemberWithdrawalService;
import org.example._nd_project.member.TimeTransactionHistoryView;
import org.example._nd_project.security.MemberPrincipal;
import org.example._nd_project.task.TaskService;
import org.example._nd_project.submission.ReviewRepository;
import org.example._nd_project.volunteer.VolunteerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ProfileController.class)
class ProfileControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean MemberService memberService;
    @MockitoBean MemberWithdrawalService memberWithdrawalService;
    @MockitoBean TaskService taskService;
    @MockitoBean VolunteerService volunteerService;
    @MockitoBean ReviewRepository reviewRepository;

    @Test
    void myProfileTemplateRenders() throws Exception {
        MemberPrincipal principal = new MemberPrincipal(
                3L, "member@example.com", "password", "회원", "USER", true
        );
        when(memberService.getProfile(3L)).thenReturn(new MemberProfileView(
                3L, "member@example.com", "회원", "도움을 드립니다.", false,
                null, List.of("Spring"), true, 0, 0, 0.0, 120, 0, Instant.now()
        ));
        when(memberService.getTimeTransactionHistory(3L, 10)).thenReturn(List.of(
                new TimeTransactionHistoryView("가입 축하 품 지급", "신규 회원 체험 시간 지급", 4,
                        true, 4, 0, Instant.now())
        ));
        when(memberService.getTimeTransactionHistoryCount(3L)).thenReturn(11L);
        when(taskService.findRegisteredTasks(3L)).thenReturn(List.of());
        when(taskService.findWorkingTasks(3L)).thenReturn(List.of());
        when(volunteerService.findAppliedTasks(3L)).thenReturn(List.of());

        mockMvc.perform(get("/profile").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("profile"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("TIME CREDIT")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("가입 축하 품 지급")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("이용 내역 더보기")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-time-history-more")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("회원 프로필 | D3BUG")));

        when(memberService.getTimeTransactionHistory(3L, 20)).thenReturn(List.of(
                new TimeTransactionHistoryView("가입 축하 품 지급", "신규 회원 체험 시간 지급", 4,
                        true, 4, 0, Instant.now())
        ));

        mockMvc.perform(get("/profile").param("historySize", "20").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("이용 내역 접기")));
    }

    @Test
    void withdrawsAuthenticatedMemberAfterPasswordConfirmation() throws Exception {
        MemberPrincipal principal = new MemberPrincipal(
                3L, "member@example.com", "password", "member", "USER", true
        );

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/profile/withdraw")
                        .param("password", "password")
                        .param("confirmed", "true")
                        .with(csrf())
                        .with(user(principal)))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/login?withdrawn"));

        verify(memberWithdrawalService).withdraw(3L, "password");
    }
}
