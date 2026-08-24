package org.example._nd_project.Controller;

import jakarta.validation.Valid;
import org.example._nd_project.security.MemberPrincipal;
import org.example._nd_project.task.TaskCreateForm;
import org.example._nd_project.task.TaskEditData;
import org.example._nd_project.task.TaskService;
import org.example._nd_project.task.TaskStorageException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import java.time.format.DateTimeFormatter;

@Controller
public class TaskController {

    private static final DateTimeFormatter DATETIME_INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/tasks")
    public String createTask(@AuthenticationPrincipal MemberPrincipal principal,
                             @Valid @ModelAttribute("taskForm") TaskCreateForm form,
                             BindingResult bindingResult,
                             @RequestParam(name = "attachment", required = false) MultipartFile attachment,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("taskForm", form);
            redirectAttributes.addFlashAttribute(
                    "org.springframework.validation.BindingResult.taskForm",
                    bindingResult
            );
            return "redirect:/?view=confirm";
        }

        try {
            taskService.create(principal.memberId(), form, attachment);
        } catch (TaskStorageException exception) {
            bindingResult.reject("attachment", exception.getMessage() + " 파일을 다시 선택해주세요.");
            redirectAttributes.addFlashAttribute("taskForm", form);
            redirectAttributes.addFlashAttribute(
                    "org.springframework.validation.BindingResult.taskForm",
                    bindingResult
            );
            return "redirect:/?view=confirm";
        }
        return "redirect:/?registered";
    }

    @GetMapping("/tasks/{taskId}/edit")
    public String editTask(@AuthenticationPrincipal MemberPrincipal principal,
                           @PathVariable Long taskId,
                           @RequestParam(name = "returnTo", defaultValue = "home") String returnTo,
                           RedirectAttributes redirectAttributes) {
        TaskEditData editData = taskService.getEditData(taskId, principal.memberId());
        redirectAttributes.addFlashAttribute("taskForm", editData.form());
        redirectAttributes.addFlashAttribute("editingTaskId", taskId);
        redirectAttributes.addFlashAttribute("editingHasAttachment", editData.hasAttachment());
        redirectAttributes.addFlashAttribute("editReturnTo", normalizeReturnTo(returnTo));
        redirectAttributes.addFlashAttribute(
                "maxDeadline",
                DATETIME_INPUT.format(editData.maximumDeadline())
        );
        return "redirect:/?view=confirm";
    }

    @PostMapping("/tasks/{taskId}")
    public String updateTask(@AuthenticationPrincipal MemberPrincipal principal,
                             @PathVariable Long taskId,
                             @RequestParam(name = "returnTo", defaultValue = "home") String returnTo,
                             @Valid @ModelAttribute("taskForm") TaskCreateForm form,
                             BindingResult bindingResult,
                             @RequestParam(name = "attachment", required = false) MultipartFile attachment,
                             RedirectAttributes redirectAttributes) {
        String safeReturnTo = normalizeReturnTo(returnTo);
        if (bindingResult.hasErrors()) {
            preserveEditForm(taskId, principal.memberId(), safeReturnTo, form, bindingResult, redirectAttributes);
            return "redirect:/?view=confirm";
        }

        try {
            taskService.update(taskId, principal.memberId(), form, attachment);
        } catch (TaskStorageException exception) {
            bindingResult.reject("attachment", exception.getMessage() + " 파일을 다시 선택해주세요.");
            preserveEditForm(taskId, principal.memberId(), safeReturnTo, form, bindingResult, redirectAttributes);
            return "redirect:/?view=confirm";
        }
        return redirectAfterChange(safeReturnTo, "updatedTask");
    }

    @PostMapping("/tasks/{taskId}/delete")
    public String deleteTask(@AuthenticationPrincipal MemberPrincipal principal,
                             @PathVariable Long taskId,
                             @RequestParam(name = "returnTo", defaultValue = "home") String returnTo) {
        String safeReturnTo = normalizeReturnTo(returnTo);
        taskService.delete(taskId, principal.memberId());
        return redirectAfterChange(safeReturnTo, "deletedTask");
    }

    @GetMapping("/tasks/{taskId}/attachment")
    public RedirectView downloadAttachment(@AuthenticationPrincipal MemberPrincipal principal,
                                           @PathVariable Long taskId) {
        return new RedirectView(
                taskService.createAttachmentDownloadUrl(taskId, principal.memberId()).toString()
        );
    }

    private void preserveEditForm(Long taskId, Long requesterId, String returnTo, TaskCreateForm form,
                                  BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        TaskEditData editData = taskService.getEditData(taskId, requesterId);
        redirectAttributes.addFlashAttribute("taskForm", form);
        redirectAttributes.addFlashAttribute(
                "org.springframework.validation.BindingResult.taskForm",
                bindingResult
        );
        redirectAttributes.addFlashAttribute("editingTaskId", taskId);
        redirectAttributes.addFlashAttribute("editingHasAttachment", editData.hasAttachment());
        redirectAttributes.addFlashAttribute("editReturnTo", returnTo);
        redirectAttributes.addFlashAttribute("maxDeadline", DATETIME_INPUT.format(editData.maximumDeadline()));
    }

    private String normalizeReturnTo(String returnTo) {
        return "profile".equals(returnTo) ? "profile" : "home";
    }

    private String redirectAfterChange(String returnTo, String resultParameter) {
        return "profile".equals(returnTo)
                ? "redirect:/profile?" + resultParameter
                : "redirect:/?" + resultParameter;
    }
}
