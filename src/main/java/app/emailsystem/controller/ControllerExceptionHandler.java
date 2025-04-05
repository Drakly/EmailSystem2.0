package app.emailsystem.controller;

import app.emailsystem.exception.EmailSystemException;
import app.emailsystem.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Global exception handler for controllers
 */
@ControllerAdvice
public class ControllerExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(ControllerExceptionHandler.class);
    
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleResourceNotFound(ResourceNotFoundException ex, Model model) {
        log.error("Resource not found: {}", ex.getMessage());
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("errorCode", 404);
        model.addAttribute("errorTitle", "Resource Not Found");
        return "error/custom-error";
    }
    
    @ExceptionHandler(EmailSystemException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleEmailSystemException(EmailSystemException ex, Model model) {
        log.error("Email system error: {}", ex.getMessage());
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("errorCode", 400);
        model.addAttribute("errorTitle", "Application Error");
        return "error/custom-error";
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(AccessDeniedException ex, Model model) {
        log.error("Access denied: {}", ex.getMessage());
        model.addAttribute("errorMessage", "You do not have permission to access this resource");
        model.addAttribute("errorCode", 403);
        model.addAttribute("errorTitle", "Access Denied");
        return "error/custom-error";
    }
    
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGenericException(Exception ex, Model model) {
        log.error("Internal server error: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "An unexpected error occurred. Please try again later.");
        model.addAttribute("errorCode", 500);
        model.addAttribute("errorTitle", "Internal Server Error");
        return "error/custom-error";
    }
} 