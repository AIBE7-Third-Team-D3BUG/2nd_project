package org.example._nd_project.member;

public class DuplicateMemberException extends RuntimeException {
    private final String field;

    public DuplicateMemberException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
