package org.example._nd_project.submission;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.net.URI;

public class SubmissionForm {

    @NotBlank(message = "작업 내용을 입력해주세요.")
    @Size(max = 5000, message = "작업 내용은 5,000자 이내로 입력해주세요.")
    private String resultDescription;

    @Size(max = 1500, message = "결과 링크는 1,500자 이내로 입력해주세요.")
    private String resultLink;

    @AssertTrue(message = "결과 링크는 http:// 또는 https:// 주소여야 합니다.")
    public boolean isResultLinkValid() {
        if (resultLink == null || resultLink.isBlank()) {
            return true;
        }
        try {
            URI uri = URI.create(resultLink.trim());
            return uri.getHost() != null
                    && ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public String getResultDescription() { return resultDescription; }
    public void setResultDescription(String resultDescription) { this.resultDescription = resultDescription; }
    public String getResultLink() { return resultLink; }
    public void setResultLink(String resultLink) { this.resultLink = resultLink; }
}
