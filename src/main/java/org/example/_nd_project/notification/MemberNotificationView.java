package org.example._nd_project.notification;

public record MemberNotificationView(
        Long id,
        String type,
        String tone,
        String title,
        String message,
        String targetUrl,
        boolean read,
        String createdAtLabel
) {
}
