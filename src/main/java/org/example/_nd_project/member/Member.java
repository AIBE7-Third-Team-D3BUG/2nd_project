package org.example._nd_project.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "members")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, unique = true, length = 30)
    private String nickname;

    @Column(length = 1000)
    private String introduction;

    @Column(name = "profile_image_url", length = 1000)
    private String profileImageUrl;

    @Column(name = "portfolio_url", length = 1000)
    private String portfolioUrl;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "skill_tags", nullable = false, columnDefinition = "varchar(50)[]")
    private String[] skillTags = new String[0];

    @Column(name = "notification_enabled", nullable = false)
    private boolean notificationEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MemberRole role = MemberRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MemberStatus status = MemberStatus.ACTIVE;

    @Column(name = "completed_task_count", nullable = false)
    private int completedTaskCount;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(name = "rating_sum", nullable = false)
    private int ratingSum;

    @Column(name = "terms_agreed_at", nullable = false)
    private Instant termsAgreedAt;

    @Column(name = "privacy_agreed_at", nullable = false)
    private Instant privacyAgreedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Member() {
    }

    private Member(String email, String passwordHash, String nickname, Instant agreedAt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.termsAgreedAt = agreedAt;
        this.privacyAgreedAt = agreedAt;
    }

    public static Member register(String email, String passwordHash, String nickname, Instant agreedAt) {
        return new Member(email, passwordHash, nickname, agreedAt);
    }

    public void updateProfile(String nickname, String introduction, String portfolioUrl, String[] skillTags,
                              boolean notificationEnabled) {
        this.nickname = nickname;
        this.introduction = introduction;
        this.portfolioUrl = portfolioUrl;
        this.skillTags = skillTags.clone();
        this.notificationEnabled = notificationEnabled;
    }

    public void replaceProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void recordLogin(Instant loginAt) {
        this.lastLoginAt = loginAt;
    }

    public void changeStatus(MemberStatus status) {
        if (status == null || status == MemberStatus.WITHDRAWN) {
            throw new IllegalArgumentException("관리자 화면에서는 활성 또는 정지 상태만 설정할 수 있습니다.");
        }
        this.status = status;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getNickname() { return nickname; }
    public String getIntroduction() { return introduction; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public String getPortfolioUrl() { return portfolioUrl; }
    public String[] getSkillTags() { return skillTags.clone(); }
    public boolean isNotificationEnabled() { return notificationEnabled; }
    public MemberRole getRole() { return role; }
    public MemberStatus getStatus() { return status; }
    public int getCompletedTaskCount() { return completedTaskCount; }
    public int getReviewCount() { return reviewCount; }
    public int getRatingSum() { return ratingSum; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Instant getCreatedAt() { return createdAt; }
}
