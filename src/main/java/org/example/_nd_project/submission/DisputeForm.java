package org.example._nd_project.submission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DisputeForm {

    @NotBlank(message = "문제 내용을 입력해주세요.")
    @Size(max = 5000, message = "문제 내용은 5,000자 이내로 입력해주세요.")
    private String description;

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
