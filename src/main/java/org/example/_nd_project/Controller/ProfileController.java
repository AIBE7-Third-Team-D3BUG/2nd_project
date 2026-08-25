package org.example._nd_project.Controller;

import jakarta.validation.Valid;
import org.example._nd_project.member.DuplicateMemberException;
import org.example._nd_project.member.MemberProfileView;
import org.example._nd_project.member.MemberService;
import org.example._nd_project.member.ProfileUpdateForm;
import org.example._nd_project.security.MemberPrincipal;
import org.example._nd_project.task.TaskService;
import org.example._nd_project.task.TaskStorageException;
import org.example._nd_project.volunteer.VolunteerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

@Controller
public class ProfileController {

    private final MemberService memberService;
    private final TaskService taskService;
    private final VolunteerService volunteerService;

    public ProfileController(MemberService memberService, TaskService taskService, VolunteerService volunteerService) {
        this.memberService = memberService;
        this.taskService = taskService;
        this.volunteerService = volunteerService;
    }

    @GetMapping("/profile")
    public String myProfile(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        addProfileModel(model, principal.memberId(), true);
        return "profile";
    }

    @GetMapping("/members/{memberId}")
    public String publicProfile(@PathVariable Long memberId,
                                @AuthenticationPrincipal MemberPrincipal principal,
                                Model model) {
        addProfileModel(model, memberId, principal != null && memberId.equals(principal.memberId()));
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

    @GetMapping("/members/{memberId}/image")
    public RedirectView profileImage(@PathVariable Long memberId) {
        return new RedirectView(memberService.createProfileImageUrl(memberId).toString());
    }

    private void addProfileModel(Model model, Long memberId, boolean isOwner) {
        taskService.expireOverdueOpenTasks();
        model.addAttribute("profile", memberService.getProfile(memberId));
        model.addAttribute("isOwner", isOwner);
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
}
