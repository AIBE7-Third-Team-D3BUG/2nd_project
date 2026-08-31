package org.example._nd_project.Controller;

import jakarta.validation.Valid;
import org.example._nd_project.member.DuplicateMemberException;
import org.example._nd_project.member.MemberService;
import org.example._nd_project.member.SignupForm;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

    private final MemberService memberService;
    private final boolean kakaoLoginEnabled;

    public AuthController(MemberService memberService,
                          @Value("${app.oauth.kakao.enabled:false}") boolean kakaoLoginEnabled) {
        this.memberService = memberService;
        this.kakaoLoginEnabled = kakaoLoginEnabled;
    }

    @GetMapping("/signup")
    public String signupForm(Model model, Authentication authentication) {
        if (isAuthenticated(authentication)) {
            return "redirect:/profile";
        }
        if (!model.containsAttribute("signupForm")) {
            model.addAttribute("signupForm", new SignupForm());
        }
        return "signup";
    }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute SignupForm signupForm, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "signup";
        }
        try {
            memberService.register(signupForm);
        } catch (DuplicateMemberException exception) {
            bindingResult.rejectValue(exception.getField(), "duplicate", exception.getMessage());
            return "signup";
        }
        return "redirect:/login?registered";
    }

    @GetMapping("/login")
    public String login(Authentication authentication, Model model) {
        if (isAuthenticated(authentication)) {
            return "redirect:/profile";
        }
        model.addAttribute("kakaoLoginEnabled", kakaoLoginEnabled);
        return "login";
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
