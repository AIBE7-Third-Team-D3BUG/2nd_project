package org.example._nd_project.member;

import org.example._nd_project.submission.WorkerDelayMetricsService;
import org.example._nd_project.task.TaskStorageService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class MemberService {

    private static final int SIGNUP_REWARD_MINUTES = 120;

    private final MemberRepository memberRepository;
    private final TimeAccountRepository timeAccountRepository;
    private final TimeTransactionRepository timeTransactionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TaskStorageService taskStorageService;
    private final WorkerDelayMetricsService workerDelayMetricsService;

    public MemberService(MemberRepository memberRepository,
                         TimeAccountRepository timeAccountRepository,
                         TimeTransactionRepository timeTransactionRepository,
                         PasswordEncoder passwordEncoder,
                         TaskStorageService taskStorageService,
                         WorkerDelayMetricsService workerDelayMetricsService) {
        this.memberRepository = memberRepository;
        this.timeAccountRepository = timeAccountRepository;
        this.timeTransactionRepository = timeTransactionRepository;
        this.passwordEncoder = passwordEncoder;
        this.taskStorageService = taskStorageService;
        this.workerDelayMetricsService = workerDelayMetricsService;
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

    @Transactional
    public Member registerWithSocialLogin(String email, String nickname) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedNickname = nickname.trim();
        if (memberRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateMemberException("email", "이미 가입된 이메일입니다. 기존 이메일 로그인으로 계정을 확인해주세요.");
        }
        if (memberRepository.existsByNickname(normalizedNickname)) {
            throw new DuplicateMemberException("nickname", "이미 사용 중인 닉네임입니다.");
        }

        try {
            Member member = memberRepository.saveAndFlush(Member.register(
                    normalizedEmail,
                    passwordEncoder.encode(UUID.randomUUID().toString()),
                    normalizedNickname,
                    Instant.now()
            ));
            timeAccountRepository.save(new TimeAccount(member.getId(), SIGNUP_REWARD_MINUTES));
            timeAccountRepository.flush();
            timeTransactionRepository.saveAndFlush(TimeTransaction.signupReward(
                    member.getId(), SIGNUP_REWARD_MINUTES, UUID.randomUUID().toString()
            ));
            return member;
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
                member.getProfileImageUrl() != null && !member.getProfileImageUrl().isBlank(),
                member.getPortfolioUrl(), Arrays.asList(member.getSkillTags()), member.isNotificationEnabled(),
                member.getCompletedTaskCount(), member.getReviewCount(), rating,
                account.getAvailableMinutes(), account.getReservedMinutes(), member.getCreatedAt(),
                workerDelayMetricsService.getForMember(memberId)
        );
    }

    @Transactional(readOnly = true)
    public List<TimeTransactionHistoryView> getTimeTransactionHistory(Long memberId, int limit) {
        requireMember(memberId);
        return timeTransactionRepository.findByAccountMemberIdOrderByCreatedAtDesc(memberId, PageRequest.of(0, limit)).stream()
                .map(this::toTimeTransactionHistoryView)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getTimeTransactionHistoryCount(Long memberId) {
        requireMember(memberId);
        return timeTransactionRepository.countByAccountMemberId(memberId);
    }

    @Transactional
    public void updateProfile(Long memberId, ProfileUpdateForm form, MultipartFile profileImage) {
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

        String previousImage = member.getProfileImageUrl();
        String uploadedImage = null;
        try {
            if (profileImage != null && !profileImage.isEmpty()) {
                uploadedImage = taskStorageService.uploadProfileImage(memberId, profileImage);
                member.replaceProfileImage(uploadedImage);
            } else if (form.isRemoveProfileImage()) {
                member.replaceProfileImage(null);
            }
            memberRepository.flush();
            if (previousImage != null && !previousImage.equals(member.getProfileImageUrl())) {
                taskStorageService.deleteQuietly(previousImage);
            }
        } catch (RuntimeException exception) {
            taskStorageService.deleteQuietly(uploadedImage);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public java.net.URI createProfileImageUrl(Long memberId) {
        Member member = requireMember(memberId);
        if (member.getProfileImageUrl() == null || member.getProfileImageUrl().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        }
        return taskStorageService.createSignedDownloadUrl(member.getProfileImageUrl());
    }

    @Transactional
    public void recordSuccessfulLogin(String email) {
        memberRepository.findByEmail(normalizeEmail(email))
                .ifPresent(member -> member.recordLogin(Instant.now()));
    }

    @Transactional
    public void recordSuccessfulLogin(Long memberId) {
        memberRepository.findById(memberId)
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

    private TimeTransactionHistoryView toTimeTransactionHistoryView(TimeTransaction transaction) {
        int changeMinutes = transaction.getAvailableDeltaMinutes() != 0
                ? transaction.getAvailableDeltaMinutes()
                : transaction.getReservedDeltaMinutes();
        return new TimeTransactionHistoryView(
                transactionLabel(transaction.getTransactionType()),
                transaction.getReason(),
                changeMinutes / 30,
                changeMinutes > 0,
                transaction.getAvailableBalanceAfter() / 30,
                transaction.getReservedBalanceAfter() / 30,
                transaction.getCreatedAt()
        );
    }

    private static String transactionLabel(String transactionType) {
        return switch (transactionType) {
            case "SIGNUP_REWARD" -> "가입 축하 품 지급";
            case "TASK_RESERVE" -> "업무 등록 품 예약";
            case "TASK_REFUND" -> "예약 품 반환";
            case "TASK_SETTLEMENT_DEBIT" -> "업무 완료 품 정산";
            case "TASK_SETTLEMENT_CREDIT" -> "업무 완료 품 지급";
            case "ADMIN_CREDIT" -> "관리자 품 지급";
            case "ADMIN_DEBIT" -> "관리자 품 차감";
            case "REVERSAL" -> "거래 취소";
            default -> "품 변동";
        };
    }
}
