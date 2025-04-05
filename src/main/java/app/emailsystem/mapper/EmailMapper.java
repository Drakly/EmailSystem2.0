package app.emailsystem.mapper;

import app.emailsystem.dto.EmailDTO;
import app.emailsystem.entity.Email;
import app.emailsystem.entity.User;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Mapper class to convert between Email entity and DTOs
 */
@Component
public class EmailMapper {

    /**
     * Convert EmailDTO to Email entity for creating a new email
     *
     * @param emailDTO the email DTO
     * @param sender the sender user
     * @return the email entity
     */
    public Email toEntity(EmailDTO emailDTO, User sender) {
        return Email.builder()
                .sender(sender)
                .subject(emailDTO.getSubject() != null ? emailDTO.getSubject() : "")
                .content(emailDTO.getContent() != null ? emailDTO.getContent() : "")
                .createdAt(LocalDateTime.now())
                .read(false)
                .sent(true)
                .draft(false)
                .trash(false)
                .starred(false)
                .build();
    }

    /**
     * Convert EmailDTO to Email entity for creating a draft email
     *
     * @param emailDTO the email DTO
     * @param sender the sender user
     * @return the email entity
     */
    public Email toDraftEntity(EmailDTO emailDTO, User sender) {
        return Email.builder()
                .sender(sender)
                .subject(emailDTO.getSubject() != null ? emailDTO.getSubject() : "")
                .content(emailDTO.getContent() != null ? emailDTO.getContent() : "")
                .createdAt(LocalDateTime.now())
                .read(false)
                .sent(false)
                .draft(true)
                .trash(false)
                .starred(false)
                .build();
    }

    /**
     * Convert Email entity to EmailDTO
     *
     * @param email the email entity
     * @return the email DTO
     */
    public EmailDTO toDto(Email email) {
        EmailDTO dto = new EmailDTO();
        dto.setId(email.getId().toString());
        dto.setSubject(email.getSubject());
        dto.setContent(email.getContent());
        
        if (email.getRecipient() != null && 
            (email.getSender() == null || !email.getRecipient().getId().equals(email.getSender().getId()))) {
            dto.setRecipients(email.getRecipient().getEmail());
        }
        
        return dto;
    }
} 