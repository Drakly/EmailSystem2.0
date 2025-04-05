package app.emailsystem.service;

import app.emailsystem.dto.AttachmentDTO;
import app.emailsystem.entity.Attachment;
import app.emailsystem.entity.Email;
import app.emailsystem.exception.EmailSystemException;
import app.emailsystem.exception.ResourceNotFoundException;
import app.emailsystem.mapper.AttachmentMapper;
import app.emailsystem.repository.AttachmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final AttachmentMapper attachmentMapper;

    @Autowired
    public AttachmentService(AttachmentRepository attachmentRepository, AttachmentMapper attachmentMapper) {
        this.attachmentRepository = attachmentRepository;
        this.attachmentMapper = attachmentMapper;
    }

    /**
     * Save attachments for an email
     * 
     * @param email the email to which attachments belong
     * @param files the files to save
     * @return list of saved attachment entities
     */
    public List<Attachment> saveAttachments(Email email, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return new ArrayList<>();
        }

        List<Attachment> savedAttachments = new ArrayList<>();

        try {
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    Attachment attachment = attachmentMapper.toEntity(file, email);
                    savedAttachments.add(attachmentRepository.save(attachment));
                    log.info("Saved attachment: {} for email ID: {}", file.getOriginalFilename(), email.getId());
                }
            }
        } catch (IOException e) {
            log.error("Failed to save attachments", e);
            throw new EmailSystemException("Failed to save attachments", e);
        }

        return savedAttachments;
    }

    /**
     * Get attachment by ID
     * 
     * @param id the attachment ID
     * @return the attachment
     */
    public Attachment getAttachment(UUID id) {
        return attachmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", "id", id));
    }

    /**
     * Get attachment as resource for downloading
     * 
     * @param id the attachment ID
     * @return the resource
     */
    public Resource getAttachmentAsResource(UUID id) {
        Attachment attachment = getAttachment(id);
        return new ByteArrayResource(attachment.getData());
    }

    /**
     * Convert attachments to DTOs
     * 
     * @param attachments the attachments to convert
     * @return list of attachment DTOs
     */
    public List<AttachmentDTO> convertToDto(List<Attachment> attachments) {
        List<AttachmentDTO> dtos = attachmentMapper.toDtoList(attachments);
        
        // Add download URLs
        for (AttachmentDTO dto : dtos) {
            String downloadUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/attachment/")
                    .path(dto.getId().toString())
                    .toUriString();
            dto.setDownloadUrl(downloadUrl);
        }
        
        return dtos;
    }

    /**
     * Convert attachment to DTO
     * 
     * @param attachment the attachment to convert
     * @return attachment DTO
     */
    public AttachmentDTO convertToDto(Attachment attachment) {
        AttachmentDTO dto = attachmentMapper.toDto(attachment);
        
        // Add download URL
        String downloadUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/attachment/")
                .path(attachment.getId().toString())
                .toUriString();
        dto.setDownloadUrl(downloadUrl);
        
        return dto;
    }

    /**
     * Delete attachment
     * 
     * @param id the attachment ID
     */
    public void deleteAttachment(UUID id) {
        attachmentRepository.deleteById(id);
        log.info("Deleted attachment with ID: {}", id);
    }

    /**
     * Get attachments by email ID
     * 
     * @param emailId the email ID
     * @return list of attachments
     */
    public List<Attachment> getAttachmentsByEmailId(UUID emailId) {
        return attachmentRepository.findByEmailId(emailId);
    }

    /**
     * Check if multiple emails have attachments
     * 
     * @param emailIds the list of email IDs to check
     * @return map of email IDs to a boolean indicating whether they have attachments
     */
    @Transactional(readOnly = true)
    public Map<UUID, Boolean> checkEmailsHaveAttachments(List<UUID> emailIds) {
        Map<UUID, Boolean> result = new HashMap<>();
        
        // Initialize all to false
        for (UUID emailId : emailIds) {
            result.put(emailId, false);
        }
        
        // Perform a batch query to get all email IDs that have attachments
        List<Object[]> emailsWithAttachmentCounts = attachmentRepository.findEmailIdsWithAttachmentCounts(emailIds);
        
        // Update the result map based on the query results
        for (Object[] row : emailsWithAttachmentCounts) {
            UUID emailId = (UUID) row[0];
            Long count = (Long) row[1];
            
            if (count > 0) {
                result.put(emailId, true);
            }
        }
        
        return result;
    }
} 