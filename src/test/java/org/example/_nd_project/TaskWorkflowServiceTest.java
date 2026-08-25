package org.example._nd_project;

import org.example._nd_project.submission.TaskWorkflowService;
import org.example._nd_project.task.Task;
import org.example._nd_project.task.TaskRepository;
import org.example._nd_project.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskWorkflowServiceTest {

    @Mock TaskRepository taskRepository;

    private TaskWorkflowService taskWorkflowService;

    @BeforeEach
    void setUp() {
        taskWorkflowService = new TaskWorkflowService(taskRepository);
    }

    @Test
    void selectedWorkerStartsMatchedTask() {
        Task task = mock(Task.class);
        when(task.isWorker(8L)).thenReturn(true);
        when(task.getStatus()).thenReturn(TaskStatus.MATCHED);
        when(taskRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(task));

        taskWorkflowService.start(10L, 8L);

        verify(task).startWork(org.mockito.ArgumentMatchers.eq(8L), any(Instant.class));
    }

    @Test
    void anotherMemberCannotStartMatchedTask() {
        Task task = mock(Task.class);
        when(task.isWorker(99L)).thenReturn(false);
        when(taskRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(task));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> taskWorkflowService.start(10L, 99L)
        );

        assertEquals(404, exception.getStatusCode().value());
        verify(task, never()).startWork(any(), any());
    }
}
