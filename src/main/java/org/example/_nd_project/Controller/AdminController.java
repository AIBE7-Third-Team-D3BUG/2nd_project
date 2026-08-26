package org.example._nd_project.Controller;

import org.example._nd_project.admin.AdminService;
import org.example._nd_project.admin.AdminMonitoringService;
import org.example._nd_project.member.MemberStatus;
import org.example._nd_project.security.MemberPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

@Controller
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final AdminService adminService;
    private final AdminMonitoringService monitoringService;

    public AdminController(AdminService adminService, AdminMonitoringService monitoringService) {
        this.adminService = adminService;
        this.monitoringService = monitoringService;
    }

    @GetMapping("/admin/chats")
    public String chats(@RequestParam(required = false, name = "q") String query,
                        @RequestParam(required = false, name = "date")
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                        Model model) {
        model.addAttribute("chatList", monitoringService.getChatRooms(query, date));
        model.addAttribute("adminSearch", normalizedQuery(query));
        model.addAttribute("adminDate", date == null ? "" : date.toString());
        return "admin/chats";
    }

    @GetMapping("/admin/chats/{roomId}")
    public String chatDetail(@PathVariable Long roomId, Model model) {
        model.addAttribute("chat", monitoringService.getChatRoom(roomId));
        return "admin/chat-detail";
    }

    @PostMapping("/admin/chat-messages/{messageId}/blind")
    public String blindMessage(@AuthenticationPrincipal MemberPrincipal principal,
                               @PathVariable Long messageId,
                               @RequestParam Long roomId,
                               @RequestParam String reason,
                               RedirectAttributes redirectAttributes) {
        return executeTo(redirectAttributes, "메시지를 블라인드 처리했습니다.",
                "/admin/chats/" + roomId,
                () -> monitoringService.blindMessage(principal.memberId(), messageId, reason));
    }

    @PostMapping("/admin/chat-messages/{messageId}/restore")
    public String restoreMessage(@AuthenticationPrincipal MemberPrincipal principal,
                                 @PathVariable Long messageId,
                                 @RequestParam Long roomId,
                                 @RequestParam String reason,
                                 RedirectAttributes redirectAttributes) {
        return executeTo(redirectAttributes, "메시지 블라인드를 해제했습니다.",
                "/admin/chats/" + roomId,
                () -> monitoringService.restoreMessage(principal.memberId(), messageId, reason));
    }

    @GetMapping("/admin/chat-messages/{messageId}/attachment")
    public String chatAttachment(@AuthenticationPrincipal MemberPrincipal principal,
                                 @PathVariable Long messageId) {
        return "redirect:" + monitoringService.createAdminAttachmentDownloadUrl(principal.memberId(), messageId);
    }

    @GetMapping("/admin/tasks/{taskId}/progress")
    public String taskProgress(@PathVariable Long taskId, Model model) {
        model.addAttribute("progress", monitoringService.getTaskProgress(taskId));
        return "admin/task-progress";
    }

    @GetMapping("/admin")
    public String dashboard(@RequestParam(defaultValue = "0") int memberPage,
                            @RequestParam(defaultValue = "0") int taskPage,
                            @RequestParam(defaultValue = "0") int transactionPage,
                            @RequestParam(defaultValue = "0") int auditPage,
                            @RequestParam(required = false, name = "q") String query,
                            @RequestParam(required = false, name = "date")
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            Model model) {
        model.addAttribute("dashboard", adminService.getDashboard(
                query, date, memberPage, taskPage, transactionPage, auditPage));
        model.addAttribute("adminSearch", normalizedQuery(query));
        model.addAttribute("adminDate", date == null ? "" : date.toString());
        return "admin/dashboard";
    }

    @PostMapping("/admin/members/{memberId}/status")
    public String changeMemberStatus(@AuthenticationPrincipal MemberPrincipal principal,
                                     @PathVariable Long memberId,
                                     @RequestParam MemberStatus status,
                                     @RequestParam(defaultValue = "0") int returnPage,
                                     @RequestParam(required = false, defaultValue = "") String returnQuery,
                                     @RequestParam(required = false, defaultValue = "") String returnDate,
                                     RedirectAttributes redirectAttributes) {
        return executeTo(redirectAttributes, "회원 상태를 변경했습니다.", memberPageRedirect(returnPage, returnQuery, returnDate),
                () -> adminService.changeMemberStatus(principal.memberId(), memberId, status));
    }

    @PostMapping("/admin/members/{memberId}/balance")
    public String adjustBalance(@AuthenticationPrincipal MemberPrincipal principal,
                                @PathVariable Long memberId,
                                @RequestParam String operation,
                                @RequestParam int pum,
                                @RequestParam String reason,
                                @RequestParam(defaultValue = "0") int returnPage,
                                @RequestParam(required = false, defaultValue = "") String returnQuery,
                                @RequestParam(required = false, defaultValue = "") String returnDate,
                                RedirectAttributes redirectAttributes) {
        return executeTo(redirectAttributes, "재화 조정을 원장에 기록했습니다.", memberPageRedirect(returnPage, returnQuery, returnDate),
                () -> adminService.adjustBalance(principal.memberId(), memberId, operation, pum, reason));
    }

    @PostMapping("/admin/tasks/{taskId}/cancel")
    public String cancelTask(@AuthenticationPrincipal MemberPrincipal principal,
                             @PathVariable Long taskId,
                             @RequestParam String reason,
                             @RequestParam(defaultValue = "0") int returnTaskPage,
                             @RequestParam(required = false, defaultValue = "") String returnQuery,
                             @RequestParam(required = false, defaultValue = "") String returnDate,
                             RedirectAttributes redirectAttributes) {
        return executeTo(redirectAttributes, "업무를 취소하고 예약 재화를 반환했습니다.",
                adminSectionRedirect("taskPage", returnTaskPage, returnQuery, returnDate, "tasks"),
                () -> adminService.cancelOpenTask(principal.memberId(), taskId, reason));
    }

    @PostMapping("/admin/disputes/{disputeId}/review")
    public String startReview(@AuthenticationPrincipal MemberPrincipal principal,
                              @PathVariable Long disputeId,
                              @RequestParam(required = false, defaultValue = "") String returnQuery,
                              @RequestParam(required = false, defaultValue = "") String returnDate,
                              RedirectAttributes redirectAttributes) {
        return executeTo(redirectAttributes, "분쟁 검토를 시작했습니다.",
                adminSectionRedirect(null, 0, returnQuery, returnDate, "disputes"),
                () -> adminService.startDisputeReview(principal.memberId(), disputeId));
    }

    @PostMapping("/admin/disputes/{disputeId}/resolve")
    public String resolveDispute(@AuthenticationPrincipal MemberPrincipal principal,
                                 @PathVariable Long disputeId,
                                 @RequestParam boolean accepted,
                                 @RequestParam String note,
                                 @RequestParam(required = false, defaultValue = "") String returnQuery,
                                 @RequestParam(required = false, defaultValue = "") String returnDate,
                                 RedirectAttributes redirectAttributes) {
        return executeTo(redirectAttributes, accepted ? "분쟁을 처리 완료했습니다." : "분쟁을 기각했습니다.",
                adminSectionRedirect(null, 0, returnQuery, returnDate, "disputes"),
                () -> adminService.resolveDispute(principal.memberId(), disputeId, accepted, note));
    }

    private String execute(RedirectAttributes redirectAttributes, String successMessage, Runnable action) {
        return executeTo(redirectAttributes, successMessage, "/admin", action);
    }

    private String memberPageRedirect(int page, String query, String date) {
        return adminSectionRedirect("memberPage", page, query, date, "members");
    }

    private String adminSectionRedirect(String pageParameter, int page, String query, String date, String fragment) {
        StringBuilder path = new StringBuilder("/admin?");
        if (pageParameter != null) path.append(pageParameter).append('=').append(Math.max(0, page)).append('&');
        String normalized = normalizedQuery(query);
        if (!normalized.isBlank()) path.append("q=").append(UriUtils.encodeQueryParam(normalized, StandardCharsets.UTF_8)).append('&');
        if (date != null && !date.isBlank()) path.append("date=").append(UriUtils.encodeQueryParam(date.trim(), StandardCharsets.UTF_8)).append('&');
        if (path.charAt(path.length() - 1) == '&') path.deleteCharAt(path.length() - 1);
        if (path.charAt(path.length() - 1) == '?') path.deleteCharAt(path.length() - 1);
        return path.append('#').append(fragment).toString();
    }

    private String normalizedQuery(String query) {
        return query == null ? "" : query.trim();
    }

    private String executeTo(RedirectAttributes redirectAttributes, String successMessage,
                             String redirectPath, Runnable action) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("adminMessage", successMessage);
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("adminError", exception.getMessage());
        }
        return "redirect:" + redirectPath;
    }
}

