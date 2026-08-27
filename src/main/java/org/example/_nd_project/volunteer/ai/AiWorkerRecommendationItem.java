package org.example._nd_project.volunteer.ai;

import java.util.List;

public record AiWorkerRecommendationItem(
        String candidateKey,
        String summary,
        List<String> strengths,
        List<String> cautions
) {
}
