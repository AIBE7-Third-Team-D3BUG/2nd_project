package org.example._nd_project.task;

public enum TaskStatus {
    OPEN("모집 중"),
    MATCHED("매칭 완료"),
    IN_PROGRESS("진행 중"),
    SUBMITTED("결과 확인"),
    COMPLETED("완료"),
    CANCELLED("취소"),
    DISPUTED("분쟁 중");

    private final String label;

    TaskStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
