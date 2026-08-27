package org.example._nd_project.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example._nd_project.member.MemberRepository;
import org.example._nd_project.member.MemberStatus;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;

@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService attempts;

    public LoginFailureHandler(MemberRepository memberRepository,
                               PasswordEncoder passwordEncoder,
                               LoginAttemptService attempts) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.attempts = attempts;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception)
            throws IOException, ServletException {
        String email = normalize(request.getParameter("email"));
        String password = request.getParameter("password");

        if (isSuspendedAccount(exception, email, password)) {
            response.sendRedirect(request.getContextPath() + "/login?suspended");
            return;
        }

        attempts.recordFailure(email, request.getRemoteAddr());
        response.sendRedirect(request.getContextPath() + "/login?error");
    }

    private boolean isSuspendedAccount(AuthenticationException exception,
                                       String email,
                                       String password) {
        if (!(exception instanceof DisabledException) || email.isBlank() || password == null) {
            return false;
        }
        return memberRepository.findByEmail(email)
                .filter(member -> member.getStatus() == MemberStatus.SUSPENDED)
                .filter(member -> passwordEncoder.matches(password, member.getPasswordHash()))
                .isPresent();
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
