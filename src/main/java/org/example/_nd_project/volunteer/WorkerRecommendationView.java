package org.example._nd_project.volunteer;

import java.util.List;

public record WorkerRecommendationView(
        int rank,
        Long volunteerId,
        Long memberId,
        String nickname,
        int suitabilityScore,
        int skillMatchPercent,
        List<String> matchedSkills,
        int categoryCompletedCount,
        double rating,
        int reviewCount,
        int deadlineMetPercent,
        int deadlineSampleCount,
        int recentDelayPoints,
        int recentDeadlineMetPercent,
        int recentDeadlineSampleCount,
        long recentLateCount,
        long recentSevereCount,
        String recentDelayStatus,
        String averageResponseLabel,
        int responseSampleCount,
        int activeTaskCount,
        String summary,
        List<String> strengths,
        List<String> cautions,
        boolean aiEnhanced
) {
}
