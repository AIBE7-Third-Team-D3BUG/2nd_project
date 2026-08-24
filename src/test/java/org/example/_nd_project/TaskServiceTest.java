package org.example._nd_project;

import org.example._nd_project.task.Task;
import org.example._nd_project.task.TaskCategory;
import org.example._nd_project.task.TaskRepository;
import org.example._nd_project.task.TaskService;
import org.example._nd_project.task.TaskStatus;
import org.example._nd_project.task.TaskStorageService;
import org.example._nd_project.task.TaskSort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock TaskRepository taskRepository;
    @Mock TaskStorageService taskStorageService;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, taskStorageService);
    }

    @Test
    void ownerCanDeleteOpenTask() {
        Task task = mock(Task.class);
        when(task.getStatus()).thenReturn(TaskStatus.OPEN);
        when(taskRepository.findByIdAndRequesterId(10L, 3L)).thenReturn(Optional.of(task));

        taskService.delete(10L, 3L);

        verify(taskRepository).delete(task);
        verify(taskRepository).flush();
    }

    @Test
    void anotherMemberCannotDeleteTask() {
        when(taskRepository.findByIdAndRequesterId(10L, 99L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> taskService.delete(10L, 99L)
        );

        assertEquals(404, exception.getStatusCode().value());
        verify(taskRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void matchedTaskCannotBeDeleted() {
        Task task = mock(Task.class);
        when(task.getStatus()).thenReturn(TaskStatus.MATCHED);
        when(taskRepository.findByIdAndRequesterId(10L, 3L)).thenReturn(Optional.of(task));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> taskService.delete(10L, 3L)
        );

        assertEquals(409, exception.getStatusCode().value());
        verify(taskRepository, never()).delete(task);
    }

    @Test
    void openTasksUseSelectedDatabaseSort() {
        when(taskRepository.findByStatusAndDeadlineAtAfter(
                eq(TaskStatus.OPEN), any(), any(Pageable.class)
        )).thenReturn(List.of());

        taskService.findOpenTasks(TaskSort.DEADLINE, null);
        taskService.findOpenTasks(TaskSort.LATEST, null);
        taskService.findOpenTasks(TaskSort.HIGHEST_PUM, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(taskRepository, times(3)).findByStatusAndDeadlineAtAfter(
                eq(TaskStatus.OPEN), any(), pageableCaptor.capture()
        );
        List<Pageable> requests = pageableCaptor.getAllValues();

        assertEquals(Sort.Direction.ASC, requests.get(0).getSort().getOrderFor("deadlineAt").getDirection());
        assertEquals(Sort.Direction.DESC, requests.get(1).getSort().getOrderFor("createdAt").getDirection());
        assertEquals(Sort.Direction.DESC, requests.get(2).getSort().getOrderFor("requestedMinutes").getDirection());
        assertEquals(Sort.Direction.ASC, requests.get(2).getSort().getOrderFor("deadlineAt").getDirection());
    }

    @Test
    void openTasksCanBeFilteredByRegistrationCategory() {
        when(taskRepository.findByStatusAndCategoryAndDeadlineAtAfter(
                eq(TaskStatus.OPEN), eq(TaskCategory.DESIGN), any(), any(Pageable.class)
        )).thenReturn(List.of());

        taskService.findOpenTasks(TaskSort.DEADLINE, TaskCategory.DESIGN);

        verify(taskRepository).findByStatusAndCategoryAndDeadlineAtAfter(
                eq(TaskStatus.OPEN), eq(TaskCategory.DESIGN), any(), any(Pageable.class)
        );
    }
}
