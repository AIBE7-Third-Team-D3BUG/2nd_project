package org.example._nd_project.volunteer;

public enum VolunteerStatus {
    APPLIED("지원 완료"),
    ACCEPTED("선택 완료"),
    REJECTED("미선택"),
    CANCELLED("지원 취소");

    private final String label;

    VolunteerStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}