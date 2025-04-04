package app.emailsystem.controller;

import app.emailsystem.entity.Attachment;
import app.emailsystem.service.AttachmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Controller
@RequestMapping("/attachment")
public class AttachmentController {

    private final AttachmentService attachmentService;

    @Autowired
    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /**
     * Download an attachment
     * 
     * @param id the attachment ID
     * @return the attachment file as a downloadable resource
     */
    @GetMapping("/{id}")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable UUID id) {
        Resource resource = attachmentService.getAttachmentAsResource(id);
        Attachment attachment = attachmentService.getAttachment(id);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachment.getFilename() + "\"")
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .contentLength(attachment.getSize())
                .body(resource);
    }
} 