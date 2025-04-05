package app.emailsystem.controller;

import app.emailsystem.entity.Attachment;
import app.emailsystem.entity.Email;
import app.emailsystem.exception.ResourceNotFoundException;
import app.emailsystem.security.CustomUserDetails;
import app.emailsystem.service.AttachmentService;
import app.emailsystem.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/attachment")
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final EmailService emailService;

    @Autowired
    public AttachmentController(AttachmentService attachmentService, EmailService emailService) {
        this.attachmentService = attachmentService;
        this.emailService = emailService;
    }

    /**
     * Download an attachment with security verification
     * 
     * @param id the attachment ID
     * @param userDetails the authenticated user
     * @return the attachment file as a downloadable resource
     */
    @GetMapping("/{id}")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable UUID id,
                                                     @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.debug("Download request for attachment ID: {}", id);
        
        Attachment attachment = attachmentService.getAttachment(id);
        if (attachment == null) {
            log.warn("Attachment not found with ID: {}", id);
            throw new ResourceNotFoundException("Attachment not found");
        }
        
        // Security check: ensure user has access to the email containing this attachment
        Email email = attachment.getEmail();
        if (email == null) {
            log.warn("Attachment has no associated email: {}", id);
            throw new ResourceNotFoundException("Invalid attachment");
        }
        
        UUID userId = userDetails.getUser().getId();
        
        // Verify the user is either the sender or recipient of the email
        if (!email.getSender().getId().equals(userId) && !email.getRecipient().getId().equals(userId)) {
            log.warn("User {} attempted to access attachment {} without permission", userId, id);
            throw new ResourceNotFoundException("Attachment not found");
        }
        
        // Get the attachment as a resource
        Resource resource = attachmentService.getAttachmentAsResource(id);
        
        log.debug("Serving attachment: {}, size: {}, type: {}", 
                attachment.getFilename(), attachment.getSize(), attachment.getContentType());
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachment.getFilename() + "\"")
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .contentLength(attachment.getSize())
                .body(resource);
    }
} 