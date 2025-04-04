package app.emailsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttachmentDTO {
    private UUID id;
    private String filename;
    private String contentType;
    private long size;
    private UUID emailId;
    private String downloadUrl;
} 