package app.emailsystem.dto;

import lombok.Data;

@Data
public class AttachmentDTO {
    private String id;
    private String fileName;
    private String fileType;
    private long fileSize;
    private byte[] fileContent;
} 