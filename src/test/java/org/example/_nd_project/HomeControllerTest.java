package org.example._nd_project;

import org.example._nd_project.Controller.HomeController;
import org.example._nd_project.member.MemberProfileView;
import org.example._nd_project.member.MemberService;
import org.example._nd_project.security.MemberPrincipal;
import org.example._nd_project.task.TaskCreateForm;
import org.example._nd_project.task.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeControllerTest {

    @Mock TaskService taskService;
    @Mock MemberService memberService;

    private HomeController homeController;

    @BeforeEach
    void setUp() {
        homeController = new HomeController(taskService, memberService);
    }

    @Test
    void memberWhoseTimeIsFullyReservedCannotCreateAnotherTask() {
        MemberPrincipal principal = principal();
        when(memberService.getProfile(3L)).thenReturn(profile(0, 120));
        ExtendedModelMap model = new ExtendedModelMap();

        homeController.home("confirm", null, null, principal, model);

        assertFalse((boolean) model.get("canCreateTask"));
        assertEquals(0, model.get("currentAvailablePum"));
        assertEquals(4, model.get("currentReservedPum"));
    }

    @Test
    void newTaskDefaultsToAnAmountTheMemberCanActuallySpend() {
        MemberPrincipal principal = principal();
        when(memberService.getProfile(3L)).thenReturn(profile(60, 60));
        ExtendedModelMap model = new ExtendedModelMap();

        homeController.home("confirm", null, null, principal, model);

        assertTrue((boolean) model.get("canCreateTask"));
        TaskCreateForm form = (TaskCreateForm) model.get("taskForm");
        assertEquals(60, form.getRequestedMinutes());
    }

    @Test
    void anonymousVisitorMustLogInBeforeCreatingTask() {
        ExtendedModelMap model = new ExtendedModelMap();

        homeController.home("confirm", null, null, null, model);

        assertFalse((boolean) model.get("canCreateTask"));
    }

    private MemberPrincipal principal() {
        return new MemberPrincipal(3L, "member@example.com", "password", "회원", "USER", true);
    }

    private MemberProfileView profile(int availableMinutes, int reservedMinutes) {
        return new MemberProfileView(
                3L,
                "member@example.com",
                "회원",
                null,
                null,
                List.of(),
                true,
                0,
                0,
                0.0,
                availableMinutes,
                reservedMinutes,
                Instant.now()
        );
    }
}
