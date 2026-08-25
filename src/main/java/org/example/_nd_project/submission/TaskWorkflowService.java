package org.example._nd_project.submission;

import org.example._nd_project.task.Task;
import org.example._nd_project.task.TaskRepository;
import org.example._nd_project.task.TaskStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
public class TaskWorkflowService {

    private final TaskRepository taskRepository;

    public TaskWorkflowService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional
    public void start(Long taskId, Long workerId) {
        Task task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!task.isWorker(workerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (task.getStatus() != TaskStatus.MATCHED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "매칭이 완료된 업무만 시작할 수 있습니다."
            );
        }
        task.startWork(workerId, Instant.now());
    }
}
