package org.example._nd_project.Controller;

import org.example._nd_project.member.MemberService;
import org.example._nd_project.member.MemberProfileView;
import org.example._nd_project.security.MemberPrincipal;
import org.example._nd_project.task.TaskCategory;
import org.example._nd_project.task.TaskCreateForm;
import org.example._nd_project.task.TaskListItem;
import org.example._nd_project.task.TaskService;
import org.example._nd_project.task.TaskSort;
import org.example._nd_project.volunteer.VolunteerCardView;
import org.example._nd_project.volunteer.VolunteerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
public class HomeController {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATETIME_INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final TaskService taskService;
    private final MemberService memberService;
    private final VolunteerService volunteerService;

    public HomeController(TaskService taskService, MemberService memberService, VolunteerService volunteerService) {
        this.taskService = taskService;
        this.memberService = memberService;
        this.volunteerService = volunteerService;
    }

    @GetMapping("/")
    public String home(@RequestParam(name = "view", required = false) String view,
                       @RequestParam(name = "taskId", required = false) Long taskId,
                       @RequestParam(name = "sort", required = false) String sort,
                       @RequestParam(name = "category", required = false) String category,
                       @AuthenticationPrincipal MemberPrincipal principal,
                       Model model) {
        taskService.expireOverdueOpenTasks();
        TaskSort selectedSort = TaskSort.from(sort);
        TaskCategory selectedCategory = parseCategory(category);
        List<TaskListItem> availableTasks = taskService.findOpenTasks(selectedSort, selectedCategory);
        model.addAttribute("availableTasks", availableTasks);
        model.addAttribute("taskSorts", TaskSort.values());
        model.addAttribute("selectedSort", selectedSort);
        model.addAttribute("taskCategories", TaskCategory.values());
        model.addAttribute("selectedCategory", selectedCategory);
        model.addAttribute("activePage", normalizeView(view));
        model.addAttribute("currentMemberId", principal == null ? null : principal.memberId());

        boolean hasActiveTask = principal != null && taskService.hasActiveTask(principal.memberId());
        Long activeTaskId = hasActiveTask ? taskService.findLatestActiveTaskId(principal.memberId()).orElse(null) : null;
        model.addAttribute("hasActiveTask", hasActiveTask);
        model.addAttribute("activeTaskId", activeTaskId);

        model.addAttribute("registeredTasks", principal != null ? taskService.findRegisteredTasks(principal.memberId()) : List.of());
        model.addAttribute("workingTasks", principal != null ? taskService.findWorkingTasks(principal.memberId()) : List.of());
        model.addAttribute("appliedTasks", principal != null ? volunteerService.findAppliedTasks(principal.memberId()) : List.of());

        TaskListItem selectedTask = null;
        if (taskId != null) {
            selectedTask = taskService.findTaskById(taskId).orElse(null);
        } else if (!availableTasks.isEmpty()) {
            selectedTask = availableTasks.get(0);
        }
        model.addAttribute("selectedTask", selectedTask);

        long applicantCount = 0;
        boolean hasApplied = false;
        List<VolunteerCardView> volunteers = List.of();
        if (selectedTask != null) {
            applicantCount = volunteerService.countApplicants(selectedTask.id());
            hasApplied = principal != null && volunteerService.hasApplied(selectedTask.id(), principal.memberId());
            volunteers = volunteerService.getVolunteers(selectedTask.id());
        }
        model.addAttribute("applicantCount", applicantCount);
        model.addAttribute("hasApplied", hasApplied);
        model.addAttribute("volunteers", volunteers);

        LocalDateTime now = LocalDateTime.now(KOREA);
        if (!model.containsAttribute("minDeadline")) {
            model.addAttribute("minDeadline", DATETIME_INPUT.format(
                    now.withSecond(0).withNano(0).plusMinutes(1)
            ));
        }
        if (!model.containsAttribute("maxDeadline")) {
            model.addAttribute("maxDeadline", DATETIME_INPUT.format(
                    now.plusHours(24).withSecond(0).withNano(0)
            ));
        }
        MemberProfileView profile = principal == null ? null : memberService.getProfile(principal.memberId());
        int availableMinutes = profile == null ? 0 : profile.availableMinutes();
        int reservedMinutes = profile == null ? 0 : profile.reservedMinutes();
        model.addAttribute("currentAvailablePum", availableMinutes / 30);
        model.addAttribute("currentReservedPum", reservedMinutes / 30);
        model.addAttribute("canCreateTask", principal != null && availableMinutes >= 30);
        if (!model.containsAttribute("maximumSpendableMinutes")) {
            model.addAttribute("maximumSpendableMinutes", availableMinutes);
        }
        if (!model.containsAttribute("taskForm")) {
            TaskCreateForm form = new TaskCreateForm();
            if (availableMinutes > 0 && availableMinutes < form.getRequestedMinutes()) {
                form.setRequestedMinutes(availableMinutes);
            }
            model.addAttribute("taskForm", form);
        }
        return "index";
    }

    private String normalizeView(String view) {
        return switch (view == null ? "" : view) {
            case "register", "confirm" -> "confirm";
            case "dashboard", "detail", "compare" -> view;
            default -> "landing";
        };
    }

    private TaskCategory parseCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        try {
            return TaskCategory.valueOf(category);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
