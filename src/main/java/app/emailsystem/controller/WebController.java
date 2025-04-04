package app.emailsystem.controller;

import app.emailsystem.dto.AttachmentDTO;
import app.emailsystem.dto.EmailDTO;
import app.emailsystem.entity.Email;
import app.emailsystem.service.AttachmentService;
import app.emailsystem.service.EmailService;
import app.emailsystem.service.UserService;
import app.emailsystem.security.CustomUserDetails;
import app.emailsystem.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Controller
public class WebController {

    private final EmailService emailService;
    private final UserService userService;
    private final AttachmentService attachmentService;
    private static final Logger log = LoggerFactory.getLogger(WebController.class);

    @Autowired
    public WebController(UserService userService, EmailService emailService, AttachmentService attachmentService) {
        this.userService = userService;
        this.emailService = emailService;
        this.attachmentService = attachmentService;
    }

   
    @GetMapping("/inbox")
    public String inbox(@AuthenticationPrincipal CustomUserDetails userDetails, 
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Page<Email> emails = emailService.getInboxEmails(userDetails.getUser().getId(), page);
        
        model.addAttribute("emails", emails);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", emails.getTotalPages());
        model.addAttribute("unreadCount", emailService.countByRecipientIdAndReadFalseAndTrashFalse(userDetails.getUser().getId()));
        return "inbox";
    }

    @GetMapping("/compose")
    public String compose(Model model, 
                         @RequestParam(required = false) UUID replyTo,
                         @RequestParam(required = false) UUID draft,
                         @RequestParam(required = false) UUID forward) {
        EmailDTO emailDTO = new EmailDTO();
        
        if (replyTo != null) {
            // If replying to an email, pre-fill some fields
            Optional<Email> originalEmail = emailService.getEmail(replyTo);
            if (originalEmail.isPresent()) {
                Email email = originalEmail.get();
                emailDTO.setRecipients(email.getSender().getEmail());
                emailDTO.setSubject("Re: " + email.getSubject());
                emailDTO.setContent("<br><br>---<br>On " + email.getCreatedAt() + ", " + 
                                  email.getSender().getFirstName() + " " + email.getSender().getLastName() + 
                                  " wrote:<br>" + email.getContent());
            }
        } else if (draft != null) {
            // If editing a draft
            Optional<Email> draftEmail = emailService.getEmail(draft);
            if (draftEmail.isPresent()) {
                Email email = draftEmail.get();
                
                // Only drafts can be edited
                if (!email.isDraft()) {
                    throw new ResourceNotFoundException("Only drafts can be edited");
                }
                
                emailDTO.setId(email.getId().toString());
                emailDTO.setSubject(email.getSubject());
                emailDTO.setContent(email.getContent());
                
                // Set recipient if not the temporary one (sender)
                if (email.getRecipient() != null && !email.getRecipient().getId().equals(email.getSender().getId())) {
                    emailDTO.setRecipients(email.getRecipient().getEmail());
                }
                
                // Convert attachments to DTOs
                if (email.getAttachments() != null && !email.getAttachments().isEmpty()) {
                    List<AttachmentDTO> attachmentDTOs = attachmentService.convertToDto(email.getAttachments());
                    emailDTO.setSavedAttachments(attachmentDTOs);
                }
                
                model.addAttribute("editing", true);
            }
        } else if (forward != null) {
            // Handle forward case
            Optional<Email> originalEmail = emailService.getEmail(forward);
            if (originalEmail.isPresent()) {
                Email email = originalEmail.get();
                
                emailDTO.setSubject("Fwd: " + email.getSubject());
                emailDTO.setContent("<br><br>---<br>Forwarded message:<br>" +
                                  "From: " + email.getSender().getEmail() + "<br>" +
                                  "Date: " + email.getCreatedAt() + "<br>" +
                                  "Subject: " + email.getSubject() + "<br>" +
                                  "To: " + email.getRecipient().getEmail() + "<br><br>" +
                                  email.getContent());
                
                // Convert attachments to DTOs for forwarding
                if (email.getAttachments() != null && !email.getAttachments().isEmpty()) {
                    List<AttachmentDTO> attachmentDTOs = attachmentService.convertToDto(email.getAttachments());
                    emailDTO.setSavedAttachments(attachmentDTOs);
                }
                
                model.addAttribute("forwarding", true);
            }
        }
        
        model.addAttribute("emailDTO", emailDTO);
        return "compose";
    }

    @PostMapping("/emails/send")
    public String sendEmail(@Valid @ModelAttribute EmailDTO emailDTO,
                          BindingResult result,
                          @AuthenticationPrincipal CustomUserDetails userDetails,
                          RedirectAttributes redirectAttributes,
                          Model model) {
        
        // Preprocess recipients to ensure proper format
        if (emailDTO.getRecipients() != null) {
            emailDTO.setRecipients(emailDTO.getRecipients().replaceAll("\\s*,\\s*", ",").trim());
        }
        
        if (result.hasErrors()) {
            model.addAttribute("error", "Please correct the errors in the form");
            return "compose";
        }
        
        try {
            List<Email> sentEmails = emailService.sendEmail(userDetails.getUser().getId(), emailDTO);
            redirectAttributes.addFlashAttribute("message", "Email sent successfully to " + sentEmails.size() + " user(s) within the system");
            return "redirect:/inbox";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to send email: " + e.getMessage());
            return "compose";
        }
    }

    @GetMapping("/sent")
    public String sent(@AuthenticationPrincipal CustomUserDetails userDetails,
                      @RequestParam(defaultValue = "0") int page,
                      Model model) {
        Page<Email> emails = emailService.getSentEmails(userDetails.getUser().getId(), page);
        
        model.addAttribute("emails", emails);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", emails.getTotalPages());
        model.addAttribute("unreadCount", emailService.countByRecipientIdAndReadFalseAndTrashFalse(userDetails.getUser().getId()));
        return "sent";
    }

    @GetMapping("/drafts")
    public String drafts(@AuthenticationPrincipal CustomUserDetails userDetails,
                        @RequestParam(defaultValue = "0") int page,
                        Model model) {
        Page<Email> emails = emailService.getDraftEmails(userDetails.getUser().getId(), page);
        
        model.addAttribute("emails", emails);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", emails.getTotalPages());
        model.addAttribute("unreadCount", emailService.countByRecipientIdAndReadFalseAndTrashFalse(userDetails.getUser().getId()));
        return "drafts";
    }

    @PostMapping("/emails/save-draft")
    @ResponseBody
    public String saveDraft(@ModelAttribute EmailDTO emailDTO, 
                            @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            log.info("Saving draft via AJAX for user: {}", userDetails.getUser().getEmail());
            
            // Preprocess recipients to ensure proper format
            if (emailDTO.getRecipients() != null) {
                emailDTO.setRecipients(emailDTO.getRecipients().replaceAll("\\s*,\\s*", ",").trim());
            }
            
            Email savedDraft = emailService.saveDraft(emailDTO, userDetails.getUser().getId());
            
            // Return JSON result with draft ID
            return "{\"success\":true,\"id\":\"" + savedDraft.getId() + "\",\"message\":\"Draft saved successfully\"}";
        } catch (Exception e) {
            log.error("Error saving draft via AJAX: {}", e.getMessage(), e);
            return "{\"success\":false,\"message\":\"Error saving draft: " + e.getMessage().replace("\"", "'") + "\"}";
        }
    }
    
    // Regular form submission for saving draft
    @PostMapping("/emails/save-draft-form")
    public String saveDraftForm(@ModelAttribute EmailDTO emailDTO,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             RedirectAttributes redirectAttributes) {
        try {
            // Preprocess recipients to ensure proper format
            if (emailDTO.getRecipients() != null) {
                emailDTO.setRecipients(emailDTO.getRecipients().replaceAll("\\s*,\\s*", ",").trim());
            }
            
            Email savedDraft = emailService.saveDraft(emailDTO, userDetails.getUser().getId());
            redirectAttributes.addFlashAttribute("message", "Draft saved successfully!");
            return "redirect:/drafts";
        } catch (Exception e) {
            log.error("Error saving draft via form: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to save draft: " + e.getMessage());
            return "redirect:/compose";
        }
    }

    @GetMapping("/trash")
    public String trash(@AuthenticationPrincipal CustomUserDetails userDetails,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Page<Email> emails = emailService.getTrashEmails(userDetails.getUser().getId(), page);
        
        model.addAttribute("emails", emails);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", emails.getTotalPages());
        model.addAttribute("unreadCount", emailService.countByRecipientIdAndReadFalseAndTrashFalse(userDetails.getUser().getId()));
        return "trash";
    }

    @PostMapping("/email/{id}/trash")
    public String moveToTrash(@PathVariable UUID id,
                            @RequestHeader(value = "Referer", required = false) String referer,
                            RedirectAttributes redirectAttributes) {
        emailService.moveToTrash(id);
        redirectAttributes.addFlashAttribute("message", "Email moved to trash");
        return referer != null ? "redirect:" + referer : "redirect:/inbox";
    }
    
    @PostMapping("/email/{id}/delete")
    public String permanentlyDeleteEmail(@PathVariable UUID id,
                                      @AuthenticationPrincipal CustomUserDetails userDetails,
                                      RedirectAttributes redirectAttributes) {
        try {
            emailService.permanentlyDeleteEmail(id, userDetails.getUser().getId());
            redirectAttributes.addFlashAttribute("message", "Email permanently deleted");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/trash";
    }

    @GetMapping("/email/{id}")
    public String viewEmail(@PathVariable UUID id, 
                          @AuthenticationPrincipal CustomUserDetails userDetails,
                          Model model) {
        Optional<Email> emailOpt = emailService.getEmail(id);
        
        if (emailOpt.isPresent()) {
            Email email = emailOpt.get();
            
            // Mark as read if current user is the recipient
            if (email.getRecipient().getId().equals(userDetails.getUser().getId()) && !email.isRead()) {
                emailService.markAsRead(id, userDetails.getUser().getId());
            }
            
            // Convert attachments to DTOs for display
            if (email.getAttachments() != null && !email.getAttachments().isEmpty()) {
                List<AttachmentDTO> attachmentDTOs = attachmentService.convertToDto(email.getAttachments());
                model.addAttribute("attachments", attachmentDTOs);
            }
            
            model.addAttribute("email", email);
            model.addAttribute("pageTitle", email.getSubject());
            return "view-email";
        } else {
            throw new ResourceNotFoundException("Email not found with id: " + id);
        }
    }

    @PostMapping("/email/{id}/restore")
    public String restoreFromTrash(@PathVariable UUID id,
                                 RedirectAttributes redirectAttributes) {
        emailService.restoreFromTrash(id);
        redirectAttributes.addFlashAttribute("message", "Email restored from trash");
        return "redirect:/trash";
    }

    @PostMapping("/email/{id}/read")
    public String markAsRead(@PathVariable UUID id,
                           @AuthenticationPrincipal CustomUserDetails userDetails,
                           @RequestHeader(value = "Referer", required = false) String referer,
                           RedirectAttributes redirectAttributes) {
        try {
            emailService.markAsRead(id, userDetails.getUser().getId());
            redirectAttributes.addFlashAttribute("message", "Email marked as read");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return referer != null ? "redirect:" + referer : "redirect:/inbox";
    }

    @PostMapping("/email/{id}/unread")
    public String markAsUnread(@PathVariable UUID id,
                             @RequestHeader(value = "Referer", required = false) String referer,
                             RedirectAttributes redirectAttributes) {
        emailService.markAsUnread(id);
        redirectAttributes.addFlashAttribute("message", "Email marked as unread");
        return referer != null ? "redirect:" + referer : "redirect:/inbox";
    }

    @PostMapping("/email/{id}/star")
    public String toggleStar(@PathVariable UUID id,
                           @RequestHeader(value = "Referer", required = false) String referer,
                           RedirectAttributes redirectAttributes) {
        emailService.toggleStar(id);
        redirectAttributes.addFlashAttribute("message", "Email star toggled");
        return referer != null ? "redirect:" + referer : "redirect:/inbox";
    }
    
    @GetMapping("/email/{id}/reply")
    public String replyToEmail(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        return "redirect:/compose?replyTo=" + id;
    }
    
    @GetMapping("/email/{id}/forward")
    public String forwardEmail(@PathVariable UUID id, 
                             Model model,
                             @AuthenticationPrincipal CustomUserDetails userDetails) {
        Optional<Email> emailOpt = emailService.getEmail(id);
        if (emailOpt.isPresent()) {
            Email email = emailOpt.get();
            
            EmailDTO emailDTO = new EmailDTO();
            emailDTO.setSubject("Fwd: " + email.getSubject());
            emailDTO.setContent("<br><br>---<br>Forwarded message:<br>" +
                              "From: " + email.getSender().getEmail() + "<br>" +
                              "Date: " + email.getCreatedAt() + "<br>" +
                              "Subject: " + email.getSubject() + "<br>" +
                              "To: " + email.getRecipient().getEmail() + "<br><br>" +
                              email.getContent());
            
            // Convert attachments to DTOs for forwarding
            if (email.getAttachments() != null && !email.getAttachments().isEmpty()) {
                List<AttachmentDTO> attachmentDTOs = attachmentService.convertToDto(email.getAttachments());
                emailDTO.setSavedAttachments(attachmentDTOs);
            }
            
            model.addAttribute("emailDTO", emailDTO);
            model.addAttribute("forwarding", true);
            return "compose";
        } else {
            throw new ResourceNotFoundException("Email not found with id: " + id);
        }
    }
    
    @GetMapping("/email/{id}/edit")
    public String editDraft(@PathVariable UUID id, Model model) {
        Optional<Email> emailOpt = emailService.getEmail(id);
        if (emailOpt.isPresent()) {
            Email email = emailOpt.get();
            
            // Only drafts can be edited
            if (!email.isDraft()) {
                throw new ResourceNotFoundException("Only drafts can be edited");
            }
            
            EmailDTO emailDTO = new EmailDTO();
            emailDTO.setId(email.getId().toString());
            emailDTO.setSubject(email.getSubject());
            emailDTO.setContent(email.getContent());
            
            // Set recipient if not the temporary one (sender)
            if (email.getRecipient() != null && !email.getRecipient().getId().equals(email.getSender().getId())) {
                emailDTO.setRecipients(email.getRecipient().getEmail());
            }
            
            // Convert attachments to DTOs
            if (email.getAttachments() != null && !email.getAttachments().isEmpty()) {
                List<AttachmentDTO> attachmentDTOs = attachmentService.convertToDto(email.getAttachments());
                emailDTO.setSavedAttachments(attachmentDTOs);
            }
            
            model.addAttribute("emailDTO", emailDTO);
            model.addAttribute("editing", true);
            return "compose";
        } else {
            throw new ResourceNotFoundException("Draft not found with id: " + id);
        }
    }
    
    /**
     * Handle bulk operations like moving multiple emails to trash
     */
    @PostMapping("/emails/trash")
    public String moveEmailsToTrash(@RequestParam("emailIds") String emailIds,
                                 @RequestHeader(value = "Referer", required = false) String referer,
                                 RedirectAttributes redirectAttributes) {
        try {
            String[] ids = emailIds.split(",");
            int count = 0;
            
            for (String id : ids) {
                try {
                    emailService.moveToTrash(UUID.fromString(id.trim()));
                    count++;
                } catch (Exception e) {
                    // Continue with other emails even if one fails
                }
            }
            
            redirectAttributes.addFlashAttribute("message", count + " email(s) moved to trash");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to move emails to trash: " + e.getMessage());
        }
        
        return referer != null ? "redirect:" + referer : "redirect:/inbox";
    }
    
    /**
     * Empty the trash by permanently deleting all emails
     */
    @PostMapping("/emails/empty-trash")
    public String emptyTrash(@AuthenticationPrincipal CustomUserDetails userDetails,
                          RedirectAttributes redirectAttributes) {
        try {
            int count = emailService.emptyTrash(userDetails.getUser().getId());
            redirectAttributes.addFlashAttribute("message", 
                count + " email" + (count != 1 ? "s" : "") + " permanently deleted");
        } catch (Exception e) {
            log.error("Error emptying trash: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to empty trash: " + e.getMessage());
        }
        
        return "redirect:/trash";
    }

    /**
     * Diagnostic endpoint to check if emails are being delivered
     */
    @GetMapping("/diagnostics/email-check")
    public String checkEmailDelivery(@AuthenticationPrincipal CustomUserDetails userDetails,
                                   Model model) {
        UUID userId = userDetails.getUser().getId();
        
        // Get all emails sent by current user
        Page<Email> sentEmails = emailService.getSentEmails(userId, 0);
        
        // Get all emails received by current user
        Page<Email> receivedEmails = emailService.getInboxEmails(userId, 0);
        
        model.addAttribute("currentUser", userDetails.getUser());
        model.addAttribute("sentCount", sentEmails.getTotalElements());
        model.addAttribute("receivedCount", receivedEmails.getTotalElements());
        model.addAttribute("sentEmails", sentEmails.getContent());
        model.addAttribute("receivedEmails", receivedEmails.getContent());
        
        return "email-diagnostics";
    }

    @PostMapping("/emails/restore")
    public String restoreEmails(@RequestParam("emailIds") String emailIds,
                              @RequestHeader(value = "Referer", required = false) String referer,
                              RedirectAttributes redirectAttributes) {
        try {
            String[] ids = emailIds.split(",");
            int count = 0;
            
            for (String id : ids) {
                try {
                    emailService.restoreFromTrash(UUID.fromString(id.trim()));
                    count++;
                } catch (Exception e) {
                    log.error("Error restoring email with ID {}: {}", id, e.getMessage());
                }
            }
            
            redirectAttributes.addFlashAttribute("message", 
                count + " email" + (count != 1 ? "s" : "") + " restored from trash");
        } catch (Exception e) {
            log.error("Error restoring emails from trash: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to restore emails from trash: " + e.getMessage());
        }
        
        return "redirect:" + (referer != null ? referer : "/trash");
    }
} 