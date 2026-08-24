package org.example._nd_project.task;

public enum TaskCategory {
    PRESENTATION("발표 자료"),
    DEVELOPMENT("개발"),
    DOCUMENT_REVIEW("문서 검토"),
    TRANSLATION("번역"),
    INTERVIEW("인터뷰"),
    PORTFOLIO("포트폴리오"),
    DESIGN("디자인"),
    DATA("데이터"),
    ETC("기타");

    private final String label;

    TaskCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
