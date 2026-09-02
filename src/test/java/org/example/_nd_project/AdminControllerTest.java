package org.example._nd_project;

import org.example._nd_project.Controller.AdminController;
import org.example._nd_project.admin.AdminDashboardView;
import org.example._nd_project.admin.AdminChatListView;
import org.example._nd_project.admin.AdminChatDetailView;
import org.example._nd_project.admin.AdminTaskProgressView;
import org.example._nd_project.admin.AdminService;
import org.example._nd_project.admin.AdminMonitoringService;
import org.example._nd_project.member.MemberService;
import org.example._nd_project.security.LoginAttemptService;
import org.example._nd_project.security.LoginFailureHandler;
import org.example._nd_project.security.MemberPrincipal;
import org.example._nd_project.security.MemberUserDetailsService;
import org.example._nd_project.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("db")
class AdminControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean AdminService adminService;
    @MockitoBean AdminMonitoringService adminMonitoringService;
    @MockitoBean MemberUserDetailsService memberUserDetailsService;
    @MockitoBean LoginAttemptService loginAttemptService;
    @MockitoBean LoginFailureHandler loginFailureHandler;
    @MockitoBean MemberService memberService;

    @Test
    void adminCanOpenDashboard() throws Exception {
        MemberPrincipal admin = new MemberPrincipal(1L, "admin@example.com", "hash", "관리자", "ADMIN", true);
        when(adminService.getDashboard(null, null, 0, 0, 0, 0)).thenReturn(emptyDashboard());

        mockMvc.perform(get("/admin").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("관리자 대시보드")));
    }

    @Test
    void normalUserIsForbiddenFromDashboard() throws Exception {
        MemberPrincipal user = new MemberPrincipal(2L, "user@example.com", "hash", "회원", "USER", true);
        mockMvc.perform(get("/admin").with(user(user)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?accessDenied"));
    }

    @Test
    void adminCanOpenChatMonitoringAndNormalUserCannot() throws Exception {
        MemberPrincipal admin = new MemberPrincipal(1L, "admin@example.com", "hash", "관리자", "ADMIN", true);
        MemberPrincipal normal = new MemberPrincipal(2L, "user@example.com", "hash", "회원", "USER", true);
        when(adminMonitoringService.getChatRooms(null, null)).thenReturn(new AdminChatListView(List.of()));

        mockMvc.perform(get("/admin/chats").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/chats"));
        mockMvc.perform(get("/admin/chats").with(user(normal)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?accessDenied"));
    }

    @Test
    void adminCanRenderChatDetailAndTaskProgress() throws Exception {
        MemberPrincipal admin = new MemberPrincipal(1L, "admin@example.com", "hash", "관리자", "ADMIN", true);
        AdminChatDetailView chat = new AdminChatDetailView(20L, 10L, "업무", "의뢰인", "작업자", 0, List.of());
        AdminTaskProgressView progress = new AdminTaskProgressView(
                10L, "업무", "IN_PROGRESS", "진행 중", "개발", "의뢰인", "작업자", 4,
                "2026.08.27 18:00", false,
                List.of(new AdminTaskProgressView.TimelineRow("작업 시작", "2026.08.26 10:00", true, true)),
                new AdminTaskProgressView.SubmissionRow(
                        "결과 제출", 4, null, false,
                        "2026.08.27 18:15", "2026.08.27 18:15",
                        "결과 제출이 15분 지연되고 있습니다.", 15, false, false,
                        "2026.08.27 18:15"
                ),
                null, null, 20L, 0);
        when(adminMonitoringService.getChatRoom(20L)).thenReturn(chat);
        when(adminMonitoringService.getTaskProgress(10L)).thenReturn(progress);

        mockMvc.perform(get("/admin/chats/20").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/chat-detail"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("관리자 열람 기록")));
        mockMvc.perform(get("/admin/tasks/10/progress").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/task-progress"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("진행 타임라인")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("최초 제출 판정")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("지연 15분")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "수정 제출 시에도 변경되지 않음")));
    }

    private AdminDashboardView emptyDashboard() {
        return new AdminDashboardView(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
