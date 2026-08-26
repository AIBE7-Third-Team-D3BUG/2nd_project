package org.example._nd_project;

import org.example._nd_project.member.Member;
import org.example._nd_project.member.MemberRepository;
import org.example._nd_project.member.MemberStatus;
import org.example._nd_project.security.LoginAttemptService;
import org.example._nd_project.security.LoginFailureHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginFailureHandlerTest {

    private MemberRepository memberRepository;
    private PasswordEncoder passwordEncoder;
    private LoginAttemptService attempts;
    private LoginFailureHandler handler;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        attempts = mock(LoginAttemptService.class);
        handler = new LoginFailureHandler(memberRepository, passwordEncoder, attempts);
    }

    @Test
    void suspendedMemberWithCorrectPasswordGetsDedicatedMessage() throws Exception {
        Member member = Member.register("user@example.com", "encoded", "사용자", Instant.now());
        member.changeStatus(MemberStatus.SUSPENDED);
        when(memberRepository.findByEmail("user@example.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("correct-password", "encoded")).thenReturn(true);

        MockHttpServletRequest request = loginRequest("USER@example.com ", "correct-password");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new DisabledException("disabled"));

        assertEquals("/login?suspended", response.getRedirectedUrl());
        verify(attempts, never()).recordFailure("user@example.com", request.getRemoteAddr());
    }

    @Test
    void wrongPasswordKeepsGenericLoginError() throws Exception {
        Member member = Member.register("user@example.com", "encoded", "사용자", Instant.now());
        member.changeStatus(MemberStatus.SUSPENDED);
        when(memberRepository.findByEmail("user@example.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("wrong-password", "encoded")).thenReturn(false);

        MockHttpServletRequest request = loginRequest("user@example.com", "wrong-password");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new DisabledException("disabled"));

        assertEquals("/login?error", response.getRedirectedUrl());
        verify(attempts).recordFailure("user@example.com", request.getRemoteAddr());
    }

    @Test
    void ordinaryAuthenticationFailureKeepsGenericLoginError() throws Exception {
        MockHttpServletRequest request = loginRequest("user@example.com", "wrong-password");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad credentials"));

        assertEquals("/login?error", response.getRedirectedUrl());
        verify(attempts).recordFailure("user@example.com", request.getRemoteAddr());
    }

    private MockHttpServletRequest loginRequest(String email, String password) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("email", email);
        request.setParameter("password", password);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
