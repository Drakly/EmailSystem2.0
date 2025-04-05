package app.emailsystem.mapper;

import app.emailsystem.dto.AttachmentDTO;
import app.emailsystem.entity.Attachment;
import app.emailsystem.entity.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mapper class to convert between Attachment entity and DTOs
 */
@Component
@RequiredArgsConstructor
public class AttachmentMapper {

    /**
     * Convert attachment entity to DTO
     *
     * @param attachment The attachment entity
     * @return The attachment DTO
     */
    public AttachmentDTO toDto(Attachment attachment) {
        AttachmentDTO dto = new AttachmentDTO();
        dto.setId(attachment.getId());
        dto.setFilename(attachment.getFilename());
        dto.setContentType(attachment.getContentType());
        dto.setSize(attachment.getSize());
        if (attachment.getEmail() != null) {
            dto.setEmailId(attachment.getEmail().getId());
        }
        return dto;
    }

    /**
     * Convert a list of attachment entities to DTOs
     *
     * @param attachments List of attachment entities
     * @return List of attachment DTOs
     */
    public List<AttachmentDTO> toDtoList(List<Attachment> attachments) {
        return attachments.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Convert MultipartFile to Attachment entity
     *
     * @param file The MultipartFile to convert
     * @param email The email to associate with the attachment
     * @return The attachment entity
     * @throws IOException If there's an error reading the file
     */
    public Attachment toEntity(MultipartFile file, Email email) throws IOException {
        String filename = file.getOriginalFilename();
        String filePath = "/attachments/" + filename;
        
        return Attachment.builder()
                .filename(filename)
                .filePath(filePath)
                .contentType(file.getContentType())
                .size(file.getSize())
                .data(file.getBytes())
                .email(email)
                .build();
    }

    /**
     * Format file size to human-readable format
     *
     * @param bytes File size in bytes
     * @return Formatted file size string
     */
    public static String formatFileSize(long bytes) {
        final long KB = 1024;
        final long MB = KB * 1024;
        
        if (bytes < KB) {
            return bytes + " B";
        } else if (bytes < MB) {
            return String.format("%.1f KB", (float) bytes / KB);
        } else {
            return String.format("%.1f MB", (float) bytes / MB);
        }
    }
} 