package app.emailsystem.controller;

import app.emailsystem.dto.AttachmentDTO;
import app.emailsystem.dto.EmailDTO;
import app.emailsystem.entity.Email;
import app.emailsystem.entity.User;
import app.emailsystem.mapper.EmailMapper;
import app.emailsystem.service.AttachmentService;
import app.emailsystem.service.EmailService;
import app.emailsystem.service.UserService;
import app.emailsystem.security.CustomUserDetails;
import app.emailsystem.exception.ResourceNotFoundException;
import app.emailsystem.exception.EmailSystemException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * Controller responsible for email composition, sending, and draft management
 */
@Controller
public class ComposeController {

    private final EmailService emailService;
    private final UserService userService;
    private final AttachmentService attachmentService;
    private final EmailMapper emailMapper;
    private static final Logger log = LoggerFactory.getLogger(ComposeController.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm:ss");

    @Autowired
    public ComposeController(UserService userService, EmailService emailService, AttachmentService attachmentService, EmailMapper emailMapper) {
        this.userService = userService;
        this.emailService = emailService;
        this.attachmentService = attachmentService;
        this.emailMapper = emailMapper;
    }

    @GetMapping("/compose")
    public String showComposeForm(@RequestParam(required = false) String to,
                                 @RequestParam(required = false) UUID replyTo,
                                 @RequestParam(required = false) UUID forwardFrom,
                                 Model model) {
        EmailDTO emailDTO = new EmailDTO();
        
        // Pre-populate to field if specified
        if (to != null && !to.isEmpty()) {
            emailDTO.setRecipients(to);
        }
        
        // If this is a reply, fetch the original email
        if (replyTo != null) {
            Optional<Email> originalEmailOpt = emailService.getEmail(replyTo);
            if (originalEmailOpt.isPresent()) {
                Email original = originalEmailOpt.get();
                emailDTO.setRecipients(original.getSender().getEmail());
                emailDTO.setSubject("Re: " + original.getSubject());
                emailDTO.setContent("\n\n----- Original Message -----\n" +
                              "From: " + original.getSender().getEmail() + "\n" +
                              "Date: " + original.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n" +
                              "Subject: " + original.getSubject() + "\n\n" +
                              original.getContent());
            }
        }
        
        // If this is a forward, fetch the original email
        if (forwardFrom != null) {
            Optional<Email> originalEmailOpt = emailService.getEmailWithAttachments(forwardFrom);
            if (originalEmailOpt.isPresent()) {
                Email original = originalEmailOpt.get();
                emailDTO.setSubject("Fwd: " + original.getSubject());
                emailDTO.setContent("\n\n----- Forwarded Message -----\n" +
                              "From: " + original.getSender().getEmail() + "\n" +
                              "To: " + original.getRecipient().getEmail() + "\n" +
                              "Date: " + original.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n" +
                              "Subject: " + original.getSubject() + "\n\n" +
                              original.getContent());
                
                // If the original email has attachments, convert them to DTOs
                if (!original.getAttachments().isEmpty()) {
                    List<AttachmentDTO> attachmentDTOs = attachmentService.convertToDto(original.getAttachments());
                    emailDTO.setSavedAttachments(attachmentDTOs);
                }
            }
        }
        
        model.addAttribute("emailDTO", emailDTO);
        model.addAttribute("isReply", replyTo != null);
        model.addAttribute("isForward", forwardFrom != null);
        
        return "compose";
    }
    
    @GetMapping("/draft/{id}/edit")
    public String editDraft(@PathVariable UUID id,
                          @AuthenticationPrincipal CustomUserDetails userDetails,
                          Model model) {
        UUID userId = userDetails.getUser().getId();
        Optional<Email> draftOpt = emailService.getEmailWithAttachments(id);
        
        if (draftOpt.isEmpty() || !draftOpt.get().isDraft()) {
            throw new ResourceNotFoundException("Draft", "id", id);
        }
        
        Email draft = draftOpt.get();
        
        // Security check: Only owner can edit draft
        if (!draft.getSender().getId().equals(userId)) {
            throw new ResourceNotFoundException("Draft", "id", id);
        }
        
        // Convert to DTO
        EmailDTO emailDTO = emailMapper.toDto(draft);
        emailDTO.setId(draft.getId().toString());
        emailDTO.setDraft(true);
        
        // Add attachments if any
        if (!draft.getAttachments().isEmpty()) {
            List<AttachmentDTO> attachmentDTOs = attachmentService.convertToDto(draft.getAttachments());
            emailDTO.setSavedAttachments(attachmentDTOs);
        }
        
        model.addAttribute("emailDTO", emailDTO);
        model.addAttribute("isDraft", true);
        
        return "compose";
    }
    
    @PostMapping(value = "/compose/send", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String sendEmail(@AuthenticationPrincipal CustomUserDetails userDetails,
                          @Valid @ModelAttribute EmailDTO emailDTO, 
                          BindingResult bindingResult,
                          RedirectAttributes redirectAttributes,
                          Model model) {
        
        if (bindingResult.hasErrors()) {
            return "compose";
        }
        
        UUID userId = userDetails.getUser().getId();
        
        try {
            // Process attachments - they are already bound to emailDTO.attachments from the form
            if (emailDTO.getAttachments() != null && !emailDTO.getAttachments().isEmpty()) {
                // Filter out empty attachments
                List<MultipartFile> validAttachments = emailDTO.getAttachments().stream()
                    .filter(file -> file != null && !file.isEmpty())
                    .collect(Collectors.toList());
                
                if (!validAttachments.isEmpty()) {
                    emailDTO.setAttachments(validAttachments);
                }
            }
            
            // Send the email
            List<Email> sentEmails = emailService.sendEmail(userId, emailDTO);
            
            // Provide feedback
            redirectAttributes.addFlashAttribute("successMessage", 
                "Email sent successfully to " + emailDTO.getRecipients());
            
            return "redirect:/sent";
        } catch (Exception e) {
            log.error("Error sending email", e);
            model.addAttribute("errorMessage", "Failed to send email: " + e.getMessage());
            model.addAttribute("emailDTO", emailDTO); // Add emailDTO back to model on error
            return "compose";
        }
    }
    
    @PostMapping(value = "/compose/save-draft", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String saveDraft(@AuthenticationPrincipal CustomUserDetails userDetails,
                          @ModelAttribute EmailDTO emailDTO,
                          RedirectAttributes redirectAttributes,
                          Model model) {
        
        UUID userId = userDetails.getUser().getId();
        
        try {
            // Process attachments - they are already bound to emailDTO.attachments from the form
            if (emailDTO.getAttachments() != null && !emailDTO.getAttachments().isEmpty()) {
                // Filter out empty attachments
                List<MultipartFile> validAttachments = emailDTO.getAttachments().stream()
                    .filter(file -> file != null && !file.isEmpty())
                    .collect(Collectors.toList());
                
                if (!validAttachments.isEmpty()) {
                    emailDTO.setAttachments(validAttachments);
                }
            }
            
            // Save as draft
            Email savedDraft = emailService.saveDraft(emailDTO, userId);
            
            // Provide feedback
            redirectAttributes.addFlashAttribute("successMessage", "Draft saved successfully");
            
            return "redirect:/drafts";
        } catch (Exception e) {
            log.error("Error saving draft", e);
            model.addAttribute("errorMessage", "Failed to save draft: " + e.getMessage());
            model.addAttribute("emailDTO", emailDTO); // Add emailDTO back to model on error
            return "compose";
        }
    }
    
    // Additional methods as needed
} 