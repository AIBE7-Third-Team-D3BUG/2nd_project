package org.example._nd_project.Controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class GlobalErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public String handleResponseStatus(ResponseStatusException exception,
                                       HttpServletRequest request,
                                       RedirectAttributes redirectAttributes) {
        HttpStatusCode status = exception.getStatusCode();
        log.warn("Handled request error: status={}, path={}, reason={}",
                status.value(), request.getRequestURI(), exception.getReason());
        return redirectToHome(redirectAttributes, messageFor(status));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public String handleMissingResource(NoResourceFoundException exception,
                                        HttpServletRequest request,
                                        RedirectAttributes redirectAttributes) {
        log.info("Requested resource was not found: path={}", request.getRequestURI());
        return redirectToHome(redirectAttributes, "요청한 페이지 또는 자료를 찾을 수 없습니다.");
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public String handleInvalidRequest(Exception exception,
                                       HttpServletRequest request,
                                       RedirectAttributes redirectAttributes) {
        log.info("Invalid request: type={}, path={}", exception.getClass().getSimpleName(), request.getRequestURI());
        return redirectToHome(redirectAttributes, "요청 정보가 올바르지 않습니다. 다시 시도해주세요.");
    }

    private String redirectToHome(RedirectAttributes redirectAttributes, String message) {
        redirectAttributes.addFlashAttribute("globalError", message);
        return "redirect:/";
    }

    private String messageFor(HttpStatusCode status) {
        if (status.value() == 400) {
            return "요청을 처리할 수 없습니다. 입력한 내용을 확인해주세요.";
        }
        if (status.value() == 403) {
            return "이 기능을 이용할 권한이 없습니다.";
        }
        if (status.value() == 404) {
            return "요청한 페이지 또는 자료를 찾을 수 없습니다.";
        }
        if (status.value() == 409) {
            return "현재 상태에서는 이 작업을 진행할 수 없습니다. 화면을 새로고침한 뒤 다시 시도해주세요.";
        }
        return "요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요.";
    }
}
