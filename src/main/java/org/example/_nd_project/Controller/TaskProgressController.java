package org.example._nd_project.Controller;

import jakarta.validation.Valid;
import org.example._nd_project.security.MemberPrincipal;
import org.example._nd_project.submission.DisputeForm;
import org.example._nd_project.submission.RevisionRequestForm;
import org.example._nd_project.submission.ReviewForm;
import org.example._nd_project.submission.SubmissionForm;
import org.example._nd_project.submission.SubmissionService;
import org.example._nd_project.submission.TaskCompletionService;
import org.example._nd_project.submission.TaskProgressService;
import org.example._nd_project.submission.TaskProgressView;
import org.example._nd_project.submission.TaskWorkflowService;
import org.example._nd_project.task.TaskService;
import org.example._nd_project.task.TaskStorageException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class TaskProgressController {

    private final TaskProgressService taskProgressService;
    private final SubmissionService submissionService;
    private final TaskCompletionService taskCompletionService;
    private final TaskWorkflowService taskWorkflowService;
    private final TaskService taskService;

    public TaskProgressController(TaskProgressService taskProgressService,
                                  SubmissionService submissionService,
                                  TaskCompletionService taskCompletionService,
                                  TaskWorkflowService taskWorkflowService,
                                  TaskService taskService) {
        this.taskProgressService = taskProgressService;
        this.submissionService = submissionService;
        this.taskCompletionService = taskCompletionService;
        this.taskWorkflowService = taskWorkflowService;
        this.taskService = taskService;
    }

    @GetMapping("/tasks/{taskId}/progress")
    public String progress(@AuthenticationPrincipal MemberPrincipal principal,
                           @PathVariable Long taskId,
                           Model model) {
        TaskProgressView progress = taskProgressService.getProgress(taskId, principal.memberId());
        model.addAttribute("progress", progress);
        if (!model.containsAttribute("submissionForm")) {
            SubmissionForm form = new SubmissionForm();
            if (progress.submission() != null) {
                form.setResultDescription(progress.submission().resultDescription());
            }
            model.addAttribute("submissionForm", form);
        }
        if (!model.containsAttribute("revisionForm")) {
            model.addAttribute("revisionForm", new RevisionRequestForm());
        }
        if (!model.containsAttribute("disputeForm")) {
            model.addAttribute("disputeForm", new DisputeForm());
        }
        if (!model.containsAttribute("reviewForm")) {
            model.addAttribute("reviewForm", new ReviewForm());
        }
        return "task-progress";
    }

    @PostMapping("/tasks/{taskId}/start")
    public String start(@AuthenticationPrincipal MemberPrincipal principal,
                        @PathVariable Long taskId) {
        taskWorkflowService.start(taskId, principal.memberId());
        return redirectToProgress(taskId) + "?started";
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public String cancelInProgressTask(@AuthenticationPrincipal MemberPrincipal principal,
                                       @PathVariable Long taskId) {
        taskService.cancelInProgressTask(taskId, principal.memberId());
        return "redirect:/profile?cancelledTask";
    }

    @PostMapping("/tasks/{taskId}/submissions")
    public String submit(@AuthenticationPrincipal MemberPrincipal principal,
                         @PathVariable Long taskId,
                         @Valid @ModelAttribute("submissionForm") SubmissionForm form,
                         BindingResult bindingResult,
                         @RequestParam(name = "resultFile", required = false) MultipartFile resultFile,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            preserveErrors("submissionForm", form, bindingResult, redirectAttributes);
            return redirectToProgress(taskId);
        }
        try {
            submissionService.submit(taskId, principal.memberId(), form, resultFile);
        } catch (TaskStorageException exception) {
            bindingResult.reject("resultFile", exception.getMessage() + " 파일을 다시 선택해주세요.");
            preserveErrors("submissionForm", form, bindingResult, redirectAttributes);
            return redirectToProgress(taskId);
        }
        return redirectToProgress(taskId) + "?submitted";
    }

    @PostMapping("/tasks/{taskId}/approve")
    public String approve(@AuthenticationPrincipal MemberPrincipal principal,
                          @PathVariable Long taskId,
                          @Valid @ModelAttribute("reviewForm") ReviewForm form,
                          BindingResult bindingResult,
                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            preserveErrors("reviewForm", form, bindingResult, redirectAttributes);
            return redirectToProgress(taskId);
        }
        taskCompletionService.approve(taskId, principal.memberId(), form);
        return redirectToProgress(taskId) + "?approved";
    }

    @PostMapping("/tasks/{taskId}/revision-requests")
    public String requestRevision(@AuthenticationPrincipal MemberPrincipal principal,
                                  @PathVariable Long taskId,
                                  @Valid @ModelAttribute("revisionForm") RevisionRequestForm form,
                                  BindingResult bindingResult,
                                  RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            preserveErrors("revisionForm", form, bindingResult, redirectAttributes);
            redirectAttributes.addFlashAttribute("openAction", "revision");
            return redirectToProgress(taskId);
        }
        taskCompletionService.requestRevision(taskId, principal.memberId(), form);
        return redirectToProgress(taskId) + "?revisionRequested";
    }

    @PostMapping("/tasks/{taskId}/disputes")
    public String reportProblem(@AuthenticationPrincipal MemberPrincipal principal,
                                @PathVariable Long taskId,
                                @Valid @ModelAttribute("disputeForm") DisputeForm form,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            preserveErrors("disputeForm", form, bindingResult, redirectAttributes);
            redirectAttributes.addFlashAttribute("openAction", "dispute");
            return redirectToProgress(taskId);
        }
        taskCompletionService.openDispute(taskId, principal.memberId(), form);
        return redirectToProgress(taskId) + "?reported";
    }

    @GetMapping("/tasks/{taskId}/result")
    public RedirectView openResult(@AuthenticationPrincipal MemberPrincipal principal,
                                   @PathVariable Long taskId) {
        return new RedirectView(
                submissionService.createResultDownloadUrl(taskId, principal.memberId()).toString()
        );
    }

    private void preserveErrors(String attributeName, Object form, BindingResult bindingResult,
                                RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute(attributeName, form);
        redirectAttributes.addFlashAttribute(
                "org.springframework.validation.BindingResult." + attributeName,
                bindingResult
        );
    }

    private String redirectToProgress(Long taskId) {
        return "redirect:/tasks/" + taskId + "/progress";
    }
}
