package org.example._nd_project.Controller;

import jakarta.validation.Valid;
import org.example._nd_project.member.DuplicateMemberException;
import org.example._nd_project.member.MemberProfileView;
import org.example._nd_project.member.MemberService;
import org.example._nd_project.member.MemberWithdrawalService;
import org.example._nd_project.member.ProfileUpdateForm;
import org.example._nd_project.notification.MemberNotificationCenter;
import org.example._nd_project.notification.MemberNotificationService;
import org.example._nd_project.security.MemberPrincipal;
import org.example._nd_project.submission.ReviewRepository;
import org.example._nd_project.task.TaskService;
import org.example._nd_project.task.TaskStorageException;
import org.example._nd_project.volunteer.VolunteerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.view.RedirectView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

@Controller
public class ProfileController {

    private static final int HISTORY_PAGE_SIZE = 10;
    private static final int MAX_HISTORY_SIZE = 1_000;

    private final MemberService memberService;
    private final ObjectProvider<MemberWithdrawalService> memberWithdrawalService;
    private final TaskService taskService;
    private final VolunteerService volunteerService;
    private final ReviewRepository reviewRepository;
    private final MemberNotificationService notificationService;

    public ProfileController(MemberService memberService,
                             ObjectProvider<MemberWithdrawalService> memberWithdrawalService,
                             TaskService taskService,
                             VolunteerService volunteerService,
                             ReviewRepository reviewRepository,
                             MemberNotificationService notificationService) {
        this.memberService = memberService;
        this.memberWithdrawalService = memberWithdrawalService;
        this.taskService = taskService;
        this.volunteerService = volunteerService;
        this.reviewRepository = reviewRepository;
        this.notificationService = notificationService;
    }

    @GetMapping("/profile")
    public String myProfile(@AuthenticationPrincipal MemberPrincipal principal,
                            @RequestParam(defaultValue = "10") int historySize,
                            Model model) {
        addProfileModel(model, principal.memberId(), true, normalizeHistorySize(historySize));
        return "profile";
    }

    @GetMapping("/members/{memberId}")
    public String publicProfile(@PathVariable Long memberId,
                                @AuthenticationPrincipal MemberPrincipal principal,
                                Model model) {
        addProfileModel(model, memberId, principal != null && memberId.equals(principal.memberId()), HISTORY_PAGE_SIZE);
        return "profile";
    }

    @GetMapping("/profile/edit")
    public String editProfile(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        MemberProfileView profile = memberService.getProfile(principal.memberId());
        model.addAttribute("profile", profile);
        model.addAttribute("profileForm", toForm(profile));
        return "profile-edit";
    }

    @PostMapping({"/profile", "/profile/edit"})
    public String updateProfile(@AuthenticationPrincipal MemberPrincipal principal,
                                @Valid @ModelAttribute("profileForm") ProfileUpdateForm form,
                                BindingResult bindingResult,
                                @RequestParam(name = "profileImage", required = false) MultipartFile profileImage,
                                Model model) {
        if (bindingResult.hasErrors()) {
            taskService.expireOverdueOpenTasks();
            model.addAttribute("profile", memberService.getProfile(principal.memberId()));
            return "profile-edit";
        }
        try {
            memberService.updateProfile(principal.memberId(), form, profileImage);
        } catch (DuplicateMemberException exception) {
            bindingResult.rejectValue(exception.getField(), "duplicate", exception.getMessage());
            taskService.expireOverdueOpenTasks();
            model.addAttribute("profile", memberService.getProfile(principal.memberId()));
            return "profile-edit";
        } catch (TaskStorageException exception) {
            bindingResult.reject("profileImage", exception.getMessage());
            model.addAttribute("profile", memberService.getProfile(principal.memberId()));
            return "profile-edit";
        }
        return "redirect:/profile?updated";
    }

    @PostMapping("/profile/withdraw")
    public String withdraw(@AuthenticationPrincipal MemberPrincipal principal,
                           @RequestParam String password,
                           @RequestParam(name = "confirmed", defaultValue = "false") boolean confirmed,
                           HttpServletRequest request) {
        if (!confirmed) {
            return "redirect:/profile?withdrawalError=confirmation";
        }
        try {
            MemberWithdrawalService withdrawalService = memberWithdrawalService.getIfAvailable();
            if (withdrawalService == null) {
                return "redirect:/profile?withdrawalError=unavailable";
            }
            withdrawalService.withdraw(principal.memberId(), password);
        } catch (IllegalArgumentException exception) {
            return "redirect:/profile?withdrawalError=password";
        } catch (IllegalStateException exception) {
            return "redirect:/profile?withdrawalError=unavailable";
        }

        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/login?withdrawn";
    }

    @PostMapping("/notifications/{notificationId}/open")
    public String openNotification(@AuthenticationPrincipal MemberPrincipal principal,
                                   @PathVariable Long notificationId) {
        return "redirect:" + notificationService.markReadAndGetTarget(
                principal.memberId(), notificationId);
    }

    @PostMapping("/notifications/read-all")
    public String readAllNotifications(@AuthenticationPrincipal MemberPrincipal principal) {
        notificationService.markAllRead(principal.memberId());
        return "redirect:/profile#notifications";
    }

    @GetMapping("/members/{memberId}/image")
    public RedirectView profileImage(@PathVariable Long memberId) {
        return new RedirectView(memberService.createProfileImageUrl(memberId).toString());
    }

    private void addProfileModel(Model model, Long memberId, boolean isOwner, int historySize) {
        taskService.expireOverdueOpenTasks();
        model.addAttribute("profile", memberService.getProfile(memberId));
        model.addAttribute("isOwner", isOwner);
        model.addAttribute("receivedReviews", reviewRepository.findReceivedReviewsByRevieweeId(memberId));
        if (isOwner) {
            model.addAttribute("notificationCenter", notificationService.getCenter(memberId));
            model.addAttribute("writtenReviews", reviewRepository.findWrittenReviewsByReviewerId(memberId));
            var timeTransactionHistory = memberService.getTimeTransactionHistory(memberId, historySize);
            long timeTransactionHistoryCount = memberService.getTimeTransactionHistoryCount(memberId);
            model.addAttribute("timeTransactionHistory", timeTransactionHistory);
            model.addAttribute("hasMoreTimeTransactionHistory",
                    historySize < MAX_HISTORY_SIZE && timeTransactionHistoryCount > timeTransactionHistory.size());
            model.addAttribute("nextHistorySize", historySize + HISTORY_PAGE_SIZE);
            model.addAttribute("isTimeTransactionHistoryExpanded", historySize > HISTORY_PAGE_SIZE);
        } else {
            model.addAttribute("notificationCenter", MemberNotificationCenter.empty());
            model.addAttribute("writtenReviews", java.util.List.of());
            model.addAttribute("timeTransactionHistory", java.util.List.of());
            model.addAttribute("hasMoreTimeTransactionHistory", false);
            model.addAttribute("isTimeTransactionHistoryExpanded", false);
        }
        model.addAttribute("registeredTasks", taskService.findRegisteredTasks(memberId));
        model.addAttribute("workingTasks", taskService.findWorkingTasks(memberId));
        model.addAttribute("assignedTasks", taskService.findWorkingTasks(memberId));
        model.addAttribute("appliedTasks", isOwner ? volunteerService.findAppliedTasks(memberId) : java.util.List.of());
    }

    private ProfileUpdateForm toForm(MemberProfileView profile) {
        ProfileUpdateForm form = new ProfileUpdateForm();
        form.setNickname(profile.nickname());
        form.setIntroduction(profile.introduction());
        form.setPortfolioUrl(profile.portfolioUrl());
        form.setSkillTags(String.join(", ", profile.skillTags()));
        form.setNotificationEnabled(profile.notificationEnabled());
        return form;
    }

    private int normalizeHistorySize(int historySize) {
        return Math.max(HISTORY_PAGE_SIZE, Math.min(historySize, MAX_HISTORY_SIZE));
    }
}
