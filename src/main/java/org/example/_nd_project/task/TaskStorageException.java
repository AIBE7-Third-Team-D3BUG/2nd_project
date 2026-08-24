package org.example._nd_project.task;

public class TaskStorageException extends RuntimeException {
    public TaskStorageException(String message) {
        super(message);
    }

    public TaskStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
