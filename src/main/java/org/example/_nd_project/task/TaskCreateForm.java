package org.example._nd_project.task;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;

public class TaskCreateForm {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    @NotBlank(message = "업무 제목을 입력해주세요.")
    @Size(max = 120, message = "업무 제목은 120자 이내로 입력해주세요.")
    private String title;

    @NotBlank(message = "상황 설명을 입력해주세요.")
    @Size(max = 3000, message = "상황 설명은 3,000자 이내로 입력해주세요.")
    private String description;

    @NotNull(message = "카테고리를 선택해주세요.")
    private TaskCategory category;

    @Size(max = 599, message = "기술 태그가 너무 깁니다.")
    private String skillTags;

    @NotNull(message = "요청 시간을 선택해주세요.")
    @Min(value = 30, message = "요청 시간은 최소 30분입니다.")
    @Max(value = 1440, message = "요청 시간은 최대 24시간입니다.")
    private Integer requestedMinutes = 120;

    @NotNull(message = "희망 마감을 입력해주세요.")
    @Future(message = "희망 마감은 현재보다 이후여야 합니다.")
    private LocalDateTime deadlineAt;

    @NotBlank(message = "완료 기준을 입력해주세요.")
    @Size(max = 500, message = "완료 기준은 500자 이내로 입력해주세요.")
    private String deliverableDescription;

    @Size(max = 1500, message = "참고 링크는 1,500자 이내로 입력해주세요.")
    private String referenceFileUrl;

    @AssertTrue(message = "요청 시간은 30분 단위로 선택해주세요.")
    public boolean isRequestedMinutesValid() {
        return requestedMinutes == null || requestedMinutes % 30 == 0;
    }

    @AssertTrue(message = "희망 마감은 등록 시점부터 24시간 이내여야 합니다.")
    public boolean isDeadlineWithin24Hours() {
        if (deadlineAt == null) {
            return true;
        }
        Duration remaining = Duration.between(LocalDateTime.now(KOREA), deadlineAt);
        return !remaining.isNegative() && remaining.compareTo(Duration.ofHours(24)) <= 0;
    }

    @AssertTrue(message = "기술 태그는 최대 10개까지 입력할 수 있습니다.")
    public boolean isSkillTagsValid() {
        return normalizedSkillTags().length <= 10;
    }

    @AssertTrue(message = "각 기술 태그는 50자 이내로 입력해주세요.")
    public boolean isSkillTagLengthsValid() {
        return Arrays.stream(normalizedSkillTags()).allMatch(tag -> tag.length() <= 50);
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

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public TaskCategory getCategory() { return category; }
    public void setCategory(TaskCategory category) { this.category = category; }
    public String getSkillTags() { return skillTags; }
    public void setSkillTags(String skillTags) { this.skillTags = skillTags; }
    public Integer getRequestedMinutes() { return requestedMinutes; }
    public void setRequestedMinutes(Integer requestedMinutes) { this.requestedMinutes = requestedMinutes; }
    public LocalDateTime getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(LocalDateTime deadlineAt) { this.deadlineAt = deadlineAt; }
    public String getDeliverableDescription() { return deliverableDescription; }
    public void setDeliverableDescription(String deliverableDescription) { this.deliverableDescription = deliverableDescription; }
    public String getReferenceFileUrl() { return referenceFileUrl; }
    public void setReferenceFileUrl(String referenceFileUrl) { this.referenceFileUrl = referenceFileUrl; }
}
