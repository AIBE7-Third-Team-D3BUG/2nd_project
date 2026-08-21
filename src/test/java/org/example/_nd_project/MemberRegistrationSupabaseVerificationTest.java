package org.example._nd_project;

import org.example._nd_project.member.Member;
import org.example._nd_project.member.MemberRepository;
import org.example._nd_project.member.MemberService;
import org.example._nd_project.member.SignupForm;
import org.example._nd_project.member.TimeAccountRepository;
import org.example._nd_project.member.TimeTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Rollback
@Tag("supabase")
class MemberRegistrationSupabaseVerificationTest {

    @Autowired MemberService memberService;
    @Autowired MemberRepository memberRepository;
    @Autowired TimeAccountRepository timeAccountRepository;
    @Autowired TimeTransactionRepository timeTransactionRepository;
    @Autowired AuthenticationManager authenticationManager;

    @Test
    void signupCreatesHashedMemberTimeAccountAndLedgerAtomically() {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        SignupForm form = new SignupForm();
        form.setEmail("verify-" + unique + "@example.invalid");
        form.setNickname("검증" + unique.substring(0, 8));
        form.setPassword("Strong!Pass123");
        form.setPasswordConfirm("Strong!Pass123");
        form.setTermsAgreed(true);
        form.setPrivacyAgreed(true);

        memberService.register(form);

        Member member = memberRepository.findByEmail(form.getEmail()).orElseThrow();
        assertThat(member.getPasswordHash()).startsWith("$2").doesNotContain(form.getPassword());
        assertThat(timeAccountRepository.findById(member.getId()).orElseThrow().getAvailableMinutes()).isEqualTo(120);
        assertThat(timeTransactionRepository.existsByIdempotencyKey("signup:" + member.getId())).isTrue();

        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(form.getEmail(), form.getPassword())
        );
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getName()).isEqualTo(form.getEmail());
    }
}
