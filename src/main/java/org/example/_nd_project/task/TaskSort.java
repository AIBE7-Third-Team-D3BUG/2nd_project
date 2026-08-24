package org.example._nd_project.task;

import org.springframework.data.domain.Sort;

public enum TaskSort {
    DEADLINE("deadline", "마감순", Sort.by(
            Sort.Order.asc("deadlineAt"),
            Sort.Order.desc("createdAt")
    )),
    LATEST("latest", "최신순", Sort.by(
            Sort.Order.desc("createdAt")
    )),
    HIGHEST_PUM("highestPum", "높은 품순", Sort.by(
            Sort.Order.desc("requestedMinutes"),
            Sort.Order.asc("deadlineAt"),
            Sort.Order.desc("createdAt")
    ));

    private final String parameter;
    private final String label;
    private final Sort sort;

    TaskSort(String parameter, String label, Sort sort) {
        this.parameter = parameter;
        this.label = label;
        this.sort = sort;
    }

    public String getParameter() {
        return parameter;
    }

    public String getLabel() {
        return label;
    }

    public Sort getSort() {
        return sort;
    }

    public static TaskSort from(String parameter) {
        if (parameter != null) {
            for (TaskSort value : values()) {
                if (value.parameter.equals(parameter)) {
                    return value;
                }
            }
        }
        return DEADLINE;
    }
}
