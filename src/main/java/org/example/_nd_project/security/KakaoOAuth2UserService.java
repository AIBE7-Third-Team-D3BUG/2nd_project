package org.example._nd_project.security;

import org.example._nd_project.member.DuplicateMemberException;
import org.example._nd_project.member.Member;
import org.example._nd_project.member.MemberRepository;
import org.example._nd_project.member.MemberService;
import org.example._nd_project.member.MemberStatus;
import org.example._nd_project.member.OAuthAccount;
import org.example._nd_project.member.OAuthAccountRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "app.oauth.kakao.enabled", havingValue = "true")
public class KakaoOAuth2UserService extends DefaultOAuth2UserService {

    private static final String PROVIDER = "kakao";
    private static final int MAX_NICKNAME_LENGTH = 30;

    private final OAuthAccountRepository oauthAccountRepository;
    private final MemberRepository memberRepository;
    private final MemberService memberService;

    public KakaoOAuth2UserService(OAuthAccountRepository oauthAccountRepository,
                                  MemberRepository memberRepository,
                                  MemberService memberService) {
        this.oauthAccountRepository = oauthAccountRepository;
        this.memberRepository = memberRepository;
        this.memberService = memberService;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User kakaoUser = super.loadUser(userRequest);
        if (!PROVIDER.equals(userRequest.getClientRegistration().getRegistrationId())) {
            return kakaoUser;
        }

        Map<String, Object> attributes = kakaoUser.getAttributes();
        String providerUserId = requiredText(attributes.get("id"), "카카오 사용자 정보를 확인할 수 없습니다.");
        OAuthAccount account = oauthAccountRepository.findByProviderAndProviderUserId(PROVIDER, providerUserId)
                .orElse(null);
        if (account != null) {
            Member member = memberRepository.findById(account.getMemberId())
                    .orElseThrow(() -> authenticationFailure("연결된 회원 정보를 찾을 수 없습니다."));
            if (member.getStatus() != MemberStatus.ACTIVE) {
                throw authenticationFailure("사용할 수 없는 계정입니다.");
            }
            return MemberPrincipal.from(member);
        }

        Map<String, Object> kakaoAccount = nestedMap(attributes, "kakao_account");
        String email = text(kakaoAccount.get("email"));
        if (email == null) {
            throw authenticationFailure("카카오 이메일 제공에 동의한 뒤 다시 시도해주세요.");
        }
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        Member existingMember = memberRepository.findByEmail(normalizedEmail).orElse(null);
        if (existingMember != null) {
            if (existingMember.getStatus() != MemberStatus.ACTIVE) {
                throw authenticationFailure("사용할 수 없는 계정입니다.");
            }
            if (oauthAccountRepository.findByMemberIdAndProvider(existingMember.getId(), PROVIDER).isPresent()) {
                throw authenticationFailure("이미 다른 카카오 계정과 연결된 이메일입니다.");
            }
            oauthAccountRepository.saveAndFlush(OAuthAccount.create(existingMember.getId(), PROVIDER, providerUserId));
            return MemberPrincipal.from(existingMember);
        }

        String nickname = availableNickname(text(nestedMap(attributes, "properties").get("nickname")));
        try {
            Member member = memberService.registerWithSocialLogin(normalizedEmail, nickname);
            oauthAccountRepository.saveAndFlush(OAuthAccount.create(member.getId(), PROVIDER, providerUserId));
            return MemberPrincipal.from(member);
        } catch (DuplicateMemberException exception) {
            throw authenticationFailure("이미 가입된 이메일 또는 닉네임입니다. 이메일 로그인 후 계정 연동을 이용해주세요.");
        }
    }

    private String availableNickname(String requestedNickname) {
        String base = requestedNickname == null ? "카카오사용자" : requestedNickname.trim();
        if (base.isBlank()) {
            base = "카카오사용자";
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String requiredText(Object value, String message) {
        String result = text(value);
        if (result == null) {
            throw authenticationFailure(message);
        }
        return result;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String result = String.valueOf(value).trim();
        return result.isBlank() ? null : result;
    }

    private OAuth2AuthenticationException authenticationFailure(String message) {
        return new OAuth2AuthenticationException(new OAuth2Error("kakao_login_failed"), message);
    }
}
