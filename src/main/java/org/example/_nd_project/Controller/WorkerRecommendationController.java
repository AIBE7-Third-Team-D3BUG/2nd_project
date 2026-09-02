package org.example._nd_project.Controller;

import org.example._nd_project.security.MemberPrincipal;
import org.example._nd_project.volunteer.WorkerRecommendationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WorkerRecommendationController {

    private final WorkerRecommendationService recommendationService;

    public WorkerRecommendationController(WorkerRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping("/tasks/{taskId}/worker-recommendations")
    public String recommend(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long taskId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            var recommendations = recommendationService.recommend(taskId, principal.memberId());
            redirectAttributes.addFlashAttribute("workerRecommendations", recommendations);
            if (recommendations.isEmpty()) {
                redirectAttributes.addFlashAttribute("recommendationError", "추천할 수 있는 지원자가 없습니다.");
            }
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("recommendationError", exception.getMessage());
        }
        return "redirect:/?view=compare&taskId=" + taskId;
    }
}
