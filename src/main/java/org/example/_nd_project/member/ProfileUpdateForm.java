package org.example._nd_project.member;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.net.URI;
import java.util.Arrays;

public class ProfileUpdateForm {

    @NotBlank(message = "닉네임을 입력해주세요.")
    @Pattern(regexp = "^[가-힣A-Za-z0-9_]{2,20}$", message = "닉네임은 한글, 영문, 숫자, 밑줄로 2~20자여야 합니다.")
    private String nickname;

    @Size(max = 1000, message = "자기소개는 1,000자 이하여야 합니다.")
    private String introduction;

    @Size(max = 1000, message = "포트폴리오 URL은 1,000자 이하여야 합니다.")
    private String portfolioUrl;

    @Size(max = 599, message = "기술 태그 입력이 너무 깁니다.")
    private String skillTags;

    private boolean notificationEnabled;

    private boolean removeProfileImage;

    @AssertTrue(message = "포트폴리오 주소는 http 또는 https URL이어야 합니다.")
    public boolean isPortfolioUrlValid() {
        if (portfolioUrl == null || portfolioUrl.isBlank()) {
            return true;
        }
        try {
            URI uri = URI.create(portfolioUrl.trim());
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    @AssertTrue(message = "기술 태그는 최대 10개이며 각 태그는 50자 이하여야 합니다.")
    public boolean isSkillTagsValid() {
        if (skillTags == null || skillTags.isBlank()) {
            return true;
        }
        String[] tags = Arrays.stream(skillTags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .toArray(String[]::new);
        return tags.length <= 10 && Arrays.stream(tags)
                .allMatch(tag -> tag.length() <= 50 && tag.matches("^[\\p{L}\\p{N}+#._ -]+$"));
    }

    public String[] normalizedSkillTags() {
        if (skillTags == null || skillTags.isBlank()) {
            return new String[0];
        }
        return Arrays.stream(skillTags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .toArray(String[]::new);
    }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getIntroduction() { return introduction; }
    public void setIntroduction(String introduction) { this.introduction = introduction; }
    public String getPortfolioUrl() { return portfolioUrl; }
    public void setPortfolioUrl(String portfolioUrl) { this.portfolioUrl = portfolioUrl; }
    public String getSkillTags() { return skillTags; }
    public void setSkillTags(String skillTags) { this.skillTags = skillTags; }
    public boolean isNotificationEnabled() { return notificationEnabled; }
    public void setNotificationEnabled(boolean notificationEnabled) { this.notificationEnabled = notificationEnabled; }
    public boolean isRemoveProfileImage() { return removeProfileImage; }
    public void setRemoveProfileImage(boolean removeProfileImage) { this.removeProfileImage = removeProfileImage; }
}
