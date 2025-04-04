package app.emailsystem.service;

import app.emailsystem.dto.AttachmentDTO;
import app.emailsystem.entity.Attachment;
import app.emailsystem.entity.Email;
import app.emailsystem.exception.ResourceNotFoundException;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;

    @Autowired
    public AttachmentService(AttachmentRepository attachmentRepository) {
        this.attachmentRepository = attachmentRepository;
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
                    Attachment attachment = Attachment.builder()
                            .filename(file.getOriginalFilename())
                            .contentType(file.getContentType())
                            .size(file.getSize())
                            .data(file.getBytes())
                            .email(email)
                            .build();

                    savedAttachments.add(attachmentRepository.save(attachment));
                    log.info("Saved attachment: {} for email ID: {}", file.getOriginalFilename(), email.getId());
                }
            }
        } catch (IOException e) {
            log.error("Failed to save attachments", e);
            throw new RuntimeException("Failed to save attachments: " + e.getMessage());
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
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found with id: " + id));
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
        return attachments.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Convert attachment to DTO
     * 
     * @param attachment the attachment to convert
     * @return attachment DTO
     */
    public AttachmentDTO convertToDto(Attachment attachment) {
        String downloadUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/attachment/")
                .path(attachment.getId().toString())
                .toUriString();

        return AttachmentDTO.builder()
                .id(attachment.getId())
                .filename(attachment.getFilename())
                .contentType(attachment.getContentType())
                .size(attachment.getSize())
                .emailId(attachment.getEmail().getId())
                .downloadUrl(downloadUrl)
                .build();
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
} 