package org.example._nd_project.volunteer;

import java.time.Instant;
import java.util.List;

public record VolunteerCardView(
        Long id,
        Long memberId,
        String nickname,
        String avatarText,
        int completedCount,
        String ratingText,
        List<String> skillTags,
        String message,
        VolunteerStatus status,
        String statusLabel,
        Instant appliedAt,
        String appliedDateLabel
) {
}