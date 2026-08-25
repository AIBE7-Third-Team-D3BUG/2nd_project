package org.example._nd_project.Controller;

import jakarta.validation.Valid;
import org.example._nd_project.member.DuplicateMemberException;
import org.example._nd_project.member.MemberProfileView;
import org.example._nd_project.member.MemberService;
import org.example._nd_project.member.ProfileUpdateForm;
import org.example._nd_project.security.MemberPrincipal;
import org.example._nd_project.task.TaskService;
import org.example._nd_project.volunteer.VolunteerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

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
    public String profile(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        taskService.expireOverdueOpenTasks();
        MemberProfileView profile = memberService.getProfile(principal.memberId());
        ProfileUpdateForm form = new ProfileUpdateForm();
        form.setNickname(profile.nickname());
        form.setIntroduction(profile.introduction());
        form.setPortfolioUrl(profile.portfolioUrl());
        form.setSkillTags(String.join(", ", profile.skillTags()));
        form.setNotificationEnabled(profile.notificationEnabled());
        model.addAttribute("profile", profile);
        model.addAttribute("profileForm", form);
        addTaskLists(model, principal.memberId());
        return "profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@AuthenticationPrincipal MemberPrincipal principal,
                                @Valid @ModelAttribute("profileForm") ProfileUpdateForm form,
                                BindingResult bindingResult,
                                Model model) {
        if (bindingResult.hasErrors()) {
            taskService.expireOverdueOpenTasks();
            model.addAttribute("profile", memberService.getProfile(principal.memberId()));
            addTaskLists(model, principal.memberId());
            return "profile";
        }
        try {
            memberService.updateProfile(principal.memberId(), form);
        } catch (DuplicateMemberException exception) {
            bindingResult.rejectValue(exception.getField(), "duplicate", exception.getMessage());
            taskService.expireOverdueOpenTasks();
            model.addAttribute("profile", memberService.getProfile(principal.memberId()));
            addTaskLists(model, principal.memberId());
            return "profile";
        }
        return "redirect:/profile?updated";
    }

    private void addTaskLists(Model model, Long memberId) {
        model.addAttribute("registeredTasks", taskService.findRegisteredTasks(memberId));
        model.addAttribute("appliedTasks", volunteerService.findAppliedTasks(memberId));
        model.addAttribute("assignedTasks", taskService.findAssignedTasks(memberId));
    }
}
