package org.example._nd_project.security;

import org.example._nd_project.member.DuplicateMemberException;
import org.example._nd_project.member.Member;
import org.example._nd_project.member.MemberRepository;
import org.example._nd_project.member.MemberService;
import org.example._nd_project.member.MemberStatus;
import org.example._nd_project.member.OAuthAccount;
import org.example._nd_project.member.OAuthAccountRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

@Service
@Profile("db")
@ConditionalOnProperty(name = "app.oauth.google.enabled", havingValue = "true")
public class GoogleOAuth2UserService extends DefaultOAuth2UserService {

    private static final String PROVIDER = "google";
    private static final int MAX_NICKNAME_LENGTH = 30;

    private final OAuthAccountRepository oauthAccountRepository;
    private final MemberRepository memberRepository;
    private final MemberService memberService;

    public GoogleOAuth2UserService(OAuthAccountRepository oauthAccountRepository,
                                   MemberRepository memberRepository,
                                   MemberService memberService) {
        this.oauthAccountRepository = oauthAccountRepository;
        this.memberRepository = memberRepository;
        this.memberService = memberService;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User googleUser = super.loadUser(userRequest);
        if (!PROVIDER.equals(userRequest.getClientRegistration().getRegistrationId())) {
            return googleUser;
        }

        Map<String, Object> attributes = googleUser.getAttributes();
        String providerUserId = requiredText(attributes.get("sub"), "Google 사용자 정보를 확인할 수 없습니다.");
        OAuthAccount account = oauthAccountRepository.findByProviderAndProviderUserId(PROVIDER, providerUserId)
                .orElse(null);
        if (account != null) {
            Member member = memberRepository.findById(account.getMemberId())
                    .orElseThrow(() -> authenticationFailure("연결된 회원 정보를 찾을 수 없습니다."));
            ensureActive(member);
            return MemberPrincipal.from(member);
        }

        String email = text(attributes.get("email"));
        if (email == null || !isEmailVerified(attributes)) {
            throw authenticationFailure("Google 이메일 인증 정보를 확인할 수 없습니다.");
        }
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        Member existingMember = memberRepository.findByEmail(normalizedEmail).orElse(null);
        if (existingMember != null) {
            ensureActive(existingMember);
            if (oauthAccountRepository.findByMemberIdAndProvider(existingMember.getId(), PROVIDER).isPresent()) {
                throw authenticationFailure("이미 다른 Google 계정과 연결된 이메일입니다.");
            }
            oauthAccountRepository.saveAndFlush(OAuthAccount.create(existingMember.getId(), PROVIDER, providerUserId));
            return MemberPrincipal.from(existingMember);
        }

        try {
            Member member = memberService.registerWithSocialLogin(normalizedEmail, availableNickname(text(attributes.get("name"))));
            oauthAccountRepository.saveAndFlush(OAuthAccount.create(member.getId(), PROVIDER, providerUserId));
            return MemberPrincipal.from(member);
        } catch (DuplicateMemberException exception) {
            throw authenticationFailure("이미 가입된 이메일 또는 닉네임입니다. 다시 시도해주세요.");
        }
    }

    private void ensureActive(Member member) {
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw authenticationFailure("사용할 수 없는 계정입니다.");
        }
    }

    private String availableNickname(String requestedNickname) {
        String base = requestedNickname == null ? "Google사용자" : requestedNickname.trim();
        if (base.isBlank()) {
            base = "Google사용자";
        }
        for (int attempt = 0; attempt < 100; attempt++) {
            String suffix = attempt == 0 ? "" : "-" + (attempt + 1);
            int baseLength = Math.max(1, MAX_NICKNAME_LENGTH - suffix.length());
            String candidate = base.length() > baseLength ? base.substring(0, baseLength) : base;
            candidate += suffix;
            if (!memberRepository.existsByNickname(candidate)) {
                return candidate;
            }
        }
        throw authenticationFailure("사용 가능한 닉네임을 만들 수 없습니다. 잠시 후 다시 시도해주세요.");
    }

    private String requiredText(Object value, String message) {
        String result = text(value);
        if (result == null) {
            throw authenticationFailure(message);
        }
        return result;
    }

    private boolean isEmailVerified(Map<String, Object> attributes) {
        return Boolean.parseBoolean(String.valueOf(attributes.get("email_verified")))
                || Boolean.parseBoolean(String.valueOf(attributes.get("verified_email")));
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String result = String.valueOf(value).trim();
        return result.isBlank() ? null : result;
    }

    private OAuth2AuthenticationException authenticationFailure(String message) {
        return new OAuth2AuthenticationException(new OAuth2Error("google_login_failed"), message);
    }
}
