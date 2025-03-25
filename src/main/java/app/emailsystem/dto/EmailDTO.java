package app.emailsystem.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.List;
import jakarta.validation.constraints.Pattern;

@Data
public class EmailDTO {
    private String id;
    
    @NotBlank(message = "Recipients are required")
    @Pattern(regexp = "^[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}(\\s*,\\s*[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,})*$", 
            message = "Please provide valid email addresses separated by commas")
    private String recipients;
    
    @NotBlank(message = "Subject is required")
    @Size(max = 255, message = "Subject too long")
    private String subject;
    
    @NotBlank(message = "Content is required")
    private String content;
    
    private boolean isDraft;
    private List<AttachmentDTO> attachments;
} 