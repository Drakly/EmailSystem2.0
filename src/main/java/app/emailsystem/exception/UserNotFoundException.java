package app.emailsystem.exception;

public class UserNotFoundException extends EmailSystemException {
    public UserNotFoundException(String message) {
        super(message);
    }
} 