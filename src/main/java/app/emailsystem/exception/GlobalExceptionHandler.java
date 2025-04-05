package app.emailsystem.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.JDBCConnectionException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleResourceNotFound(ResourceNotFoundException ex, Model model, HttpServletRequest request) {
        log.error("Resource not found: {}", ex.getMessage());
        model.addAttribute("status", 404);
        model.addAttribute("error", "Not Found");
        model.addAttribute("message", ex.getMessage());
        model.addAttribute("path", request.getRequestURI());
        return "error/custom-error";
    }

    @ExceptionHandler(EmailSystemException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleEmailSystemException(EmailSystemException ex, Model model, HttpServletRequest request) {
        log.error("Email system error: {}", ex.getMessage());
        model.addAttribute("status", 400);
        model.addAttribute("error", "Bad Request");
        model.addAttribute("message", ex.getMessage());
        model.addAttribute("path", request.getRequestURI());
        return "error/custom-error";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(AccessDeniedException ex, Model model, HttpServletRequest request) {
        log.error("Access denied: {}", ex.getMessage());
        model.addAttribute("status", 403);
        model.addAttribute("error", "Forbidden");
        model.addAttribute("message", "You do not have permission to access this resource");
        model.addAttribute("path", request.getRequestURI());
        return "error/custom-error";
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String handleAuthenticationException(AuthenticationException ex, Model model, HttpServletRequest request) {
        log.error("Authentication error: {}", ex.getMessage());
        model.addAttribute("status", 401);
        model.addAttribute("error", "Unauthorized");
        model.addAttribute("message", "Authentication failed");
        model.addAttribute("path", request.getRequestURI());
        return "redirect:/login?error";
    }

    @ExceptionHandler({
        BindException.class,
        TypeMismatchException.class,
        MethodArgumentTypeMismatchException.class,
        IllegalArgumentException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(Exception ex, Model model, HttpServletRequest request) {
        log.error("Bad request: {}", ex.getMessage());
        model.addAttribute("status", 400);
        model.addAttribute("error", "Bad Request");
        model.addAttribute("message", "Invalid request parameters");
        model.addAttribute("path", request.getRequestURI());
        return "error/custom-error";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public String handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex, Model model, HttpServletRequest request) {
        log.error("Upload size exceeded: {}", ex.getMessage());
        model.addAttribute("status", 413);
        model.addAttribute("error", "Payload Too Large");
        model.addAttribute("message", "The file you are trying to upload is too large");
        model.addAttribute("path", request.getRequestURI());
        return "error/custom-error";
    }

    @ExceptionHandler({
        DataAccessException.class,
        JDBCConnectionException.class,
        JpaSystemException.class,
        TransactionSystemException.class
    })
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleDatabaseError(Exception ex, Model model, HttpServletRequest request) {
        log.error("Database error: {}", ex.getMessage(), ex);
        model.addAttribute("status", 500);
        model.addAttribute("error", "Internal Server Error");
        model.addAttribute("message", "A database error occurred. Please try again later.");
        model.addAttribute("path", request.getRequestURI());
        return "error/custom-error";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGenericException(Exception ex, Model model, HttpServletRequest request) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        model.addAttribute("status", 500);
        model.addAttribute("error", "Internal Server Error");
        model.addAttribute("message", "An unexpected error occurred. Please try again later.");
        model.addAttribute("path", request.getRequestURI());
        return "error/custom-error";
    }
} 