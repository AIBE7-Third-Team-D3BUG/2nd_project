package org.example._nd_project.submission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RevisionRequestForm {

    @NotBlank(message = "수정할 내용을 입력해주세요.")
    @Size(max = 1000, message = "수정 요청은 1,000자 이내로 입력해주세요.")
    private String requesterNote;

    public String getRequesterNote() { return requesterNote; }
    public void setRequesterNote(String requesterNote) { this.requesterNote = requesterNote; }
}
