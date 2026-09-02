package org.example._nd_project.member;

import org.example._nd_project.submission.WorkerDelayMetrics;
import org.example._nd_project.volunteer.WorkerApplicationEligibility;

import java.time.Instant;
import java.util.List;

public record MemberProfileView(
        Long id,
        String email,
        String nickname,
        String introduction,
        boolean hasProfileImage,
        String portfolioUrl,
        List<String> skillTags,
        boolean notificationEnabled,
        int completedTaskCount,
        int reviewCount,
        double averageRating,
        int availableMinutes,
        int reservedMinutes,
        Instant createdAt,
        WorkerDelayMetrics delayMetrics,
        WorkerApplicationEligibility applicationEligibility
) {
    public int availablePum() {
        return availableMinutes / 30;
    }

    public int reservedPum() {
        return reservedMinutes / 30;
    }
}
