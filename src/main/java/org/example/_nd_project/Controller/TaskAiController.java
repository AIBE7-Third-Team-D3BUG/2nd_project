package org.example._nd_project.Controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.example._nd_project.task.ai.AiTaskDraft;
import org.example._nd_project.task.ai.TaskAiException;
import org.example._nd_project.task.ai.TaskAiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/tasks/ai")
public class TaskAiController {

    private final TaskAiService taskAiService;

    public TaskAiController(TaskAiService taskAiService) {
        this.taskAiService = taskAiService;
    }

    @PostMapping("/draft")
    public AiTaskDraft createDraft(@Valid @RequestBody AiTaskDraftRequest request) {
        return taskAiService.createDraft(request.situation());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<AiErrorResponse> handleInvalidInput(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new AiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(TaskAiException.class)
    ResponseEntity<AiErrorResponse> handleAiFailure(TaskAiException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new AiErrorResponse(exception.getMessage()));
    }

    public record AiTaskDraftRequest(
            @NotBlank(message = "상황 설명을 입력해주세요.")
            @Size(max = 3000, message = "상황 설명은 3,000자 이내로 입력해주세요.")
            String situation
    ) {
    }

    public record AiErrorResponse(String message) {
    }
}
