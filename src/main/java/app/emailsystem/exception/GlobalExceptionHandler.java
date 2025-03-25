package app.emailsystem.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailSystemException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleEmailSystemException(EmailSystemException ex, Model model) {
        log.error("Email system error: {}", ex.getMessage());
        model.addAttribute("error", ex.getMessage());
        return "error/error";
    }

    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleUserNotFoundException(UserNotFoundException ex, Model model) {
        log.error("User not found: {}", ex.getMessage());
        model.addAttribute("error", ex.getMessage());
        return "error/404";
    }

    @ExceptionHandler(EmailNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleEmailNotFoundException(EmailNotFoundException ex, Model model) {
        log.error("Email not found: {}", ex.getMessage());
        model.addAttribute("error", ex.getMessage());
        return "error/404";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDeniedException(AccessDeniedException ex, Model model) {
        log.error("Access denied: {}", ex.getMessage());
        model.addAttribute("error", "You don't have permission to access this resource");
        return "error/403";
    }

    @ExceptionHandler(AuthenticationException.class)
    public String handleAuthenticationException(AuthenticationException ex, RedirectAttributes redirectAttributes) {
        log.error("Authentication error: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("error", "Invalid username or password");
        return "redirect:/login";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleException(Exception e, HttpServletRequest request, Model model) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        model.addAttribute("message", "An unexpected error occurred");
        model.addAttribute("error", e.getMessage());
        model.addAttribute("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        model.addAttribute("path", request.getRequestURI());
        return "error/error";
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleResourceNotFoundException(ResourceNotFoundException e, Model model) {
        model.addAttribute("message", "Resource not found");
        model.addAttribute("error", e.getMessage());
        return "error/error";
    }
} 