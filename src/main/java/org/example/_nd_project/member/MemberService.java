package org.example._nd_project.member;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

@Service
public class MemberService {

    private static final int SIGNUP_REWARD_MINUTES = 120;

    private final MemberRepository memberRepository;
    private final TimeAccountRepository timeAccountRepository;
    private final TimeTransactionRepository timeTransactionRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberRepository memberRepository,
                         TimeAccountRepository timeAccountRepository,
                         TimeTransactionRepository timeTransactionRepository,
                         PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.timeAccountRepository = timeAccountRepository;
        this.timeTransactionRepository = timeTransactionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void register(SignupForm form) {
        String email = normalizeEmail(form.getEmail());
        String nickname = form.getNickname().trim();

        if (memberRepository.existsByEmail(email)) {
            throw new DuplicateMemberException("email", "이미 가입된 이메일입니다.");
        }
        if (memberRepository.existsByNickname(nickname)) {
            throw new DuplicateMemberException("nickname", "이미 사용 중인 닉네임입니다.");
        }

        try {
            Member member = memberRepository.saveAndFlush(Member.register(
                    email,
                    passwordEncoder.encode(form.getPassword()),
                    nickname,
                    Instant.now()
            ));
            timeAccountRepository.save(new TimeAccount(member.getId(), SIGNUP_REWARD_MINUTES));
            timeAccountRepository.flush();
            timeTransactionRepository.saveAndFlush(TimeTransaction.signupReward(
                    member.getId(), SIGNUP_REWARD_MINUTES, UUID.randomUUID().toString()
            ));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateMemberException("email", "이미 가입된 이메일 또는 닉네임입니다.");
        }
    }

    @Transactional(readOnly = true)
    public MemberProfileView getProfile(Long memberId) {
        Member member = requireMember(memberId);
        TimeAccount account = timeAccountRepository.findById(memberId)
                .orElseThrow(() -> new IllegalStateException("시간 계정을 찾을 수 없습니다."));
        double rating = member.getReviewCount() == 0
                ? 0.0
                : (double) member.getRatingSum() / member.getReviewCount();
        return new MemberProfileView(
                member.getId(), member.getEmail(), member.getNickname(), member.getIntroduction(),
                member.getPortfolioUrl(), Arrays.asList(member.getSkillTags()), member.isNotificationEnabled(),
                member.getCompletedTaskCount(), member.getReviewCount(), rating,
                account.getAvailableMinutes(), account.getReservedMinutes(), member.getCreatedAt()
        );
    }

    @Transactional
    public void updateProfile(Long memberId, ProfileUpdateForm form) {
        Member member = requireMember(memberId);
        String nickname = form.getNickname().trim();
        if (memberRepository.existsByNicknameAndIdNot(nickname, memberId)) {
            throw new DuplicateMemberException("nickname", "이미 사용 중인 닉네임입니다.");
        }
        member.updateProfile(
                nickname,
                normalizeNullable(form.getIntroduction()),
                normalizeNullable(form.getPortfolioUrl()),
                form.normalizedSkillTags(),
                form.isNotificationEnabled()
        );
    }

    @Transactional
    public void recordSuccessfulLogin(String email) {
        memberRepository.findByEmail(normalizeEmail(email))
                .ifPresent(member -> member.recordLogin(Instant.now()));
    }

    private Member requireMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
