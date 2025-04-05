package app.emailsystem.exception;

/**
 * General exception class for Email System application errors
 */
public class EmailSystemException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    public EmailSystemException(String message) {
        super(message);
    }
    
    public EmailSystemException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public EmailSystemException(Throwable cause) {
        super(cause);
    }
} 