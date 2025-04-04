package app.emailsystem.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailDTO {
    private String id;
    
    @NotBlank(message = "Recipients are required")
    private String recipients;
    
    @NotBlank(message = "Subject is required")
    @Size(max = 255, message = "Subject too long")
    private String subject;
    
    @NotBlank(message = "Content is required")
    private String content;
    
    private boolean isDraft;
    
    // For file uploads
    private List<MultipartFile> attachments;
    
    // For displaying attachments when viewing an email
    private List<AttachmentDTO> savedAttachments;
    
    // For processing existing attachment IDs during AJAX draft saving
    private String attachmentIds;
} 