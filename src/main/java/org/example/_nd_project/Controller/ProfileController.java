package org.example._nd_project.Controller;

import jakarta.validation.Valid;
import org.example._nd_project.member.DuplicateMemberException;
import org.example._nd_project.member.MemberProfileView;
import org.example._nd_project.member.MemberService;
import org.example._nd_project.member.ProfileUpdateForm;
import org.example._nd_project.security.MemberPrincipal;
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

    public ProfileController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        MemberProfileView profile = memberService.getProfile(principal.memberId());
        ProfileUpdateForm form = new ProfileUpdateForm();
        form.setNickname(profile.nickname());
        form.setIntroduction(profile.introduction());
        form.setPortfolioUrl(profile.portfolioUrl());
        form.setSkillTags(String.join(", ", profile.skillTags()));
        form.setNotificationEnabled(profile.notificationEnabled());
        model.addAttribute("profile", profile);
        model.addAttribute("profileForm", form);
        return "profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@AuthenticationPrincipal MemberPrincipal principal,
                                @Valid @ModelAttribute("profileForm") ProfileUpdateForm form,
                                BindingResult bindingResult,
                                Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("profile", memberService.getProfile(principal.memberId()));
            return "profile";
        }
        try {
            memberService.updateProfile(principal.memberId(), form);
        } catch (DuplicateMemberException exception) {
            bindingResult.rejectValue(exception.getField(), "duplicate", exception.getMessage());
            model.addAttribute("profile", memberService.getProfile(principal.memberId()));
            return "profile";
        }
        return "redirect:/profile?updated";
    }
}
