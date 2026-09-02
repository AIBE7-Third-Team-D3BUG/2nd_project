package org.example._nd_project.notification;

import java.util.List;

public record MemberNotificationCenter(
        long unreadCount,
        List<MemberNotificationView> notifications
) {
    public static MemberNotificationCenter empty() {
        return new MemberNotificationCenter(0, List.of());
    }
}
