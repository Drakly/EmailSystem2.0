package app.emailsystem.controller;

import app.emailsystem.dto.EmailDTO;
import app.emailsystem.entity.Email;
import app.emailsystem.entity.User;
import app.emailsystem.mapper.EmailMapper;
import app.emailsystem.service.AttachmentService;
import app.emailsystem.service.EmailService;
import app.emailsystem.service.UserService;
import app.emailsystem.security.CustomUserDetails;
import app.emailsystem.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller responsible for inbox, viewing emails and other primary email reading functions
 */
@Controller
public class WebController {

    private final EmailService emailService;
    private final UserService userService;
    private final AttachmentService attachmentService;
    private final EmailMapper emailMapper;
    private static final Logger log = LoggerFactory.getLogger(WebController.class);

    @Autowired
    public WebController(UserService userService, EmailService emailService, 
                        AttachmentService attachmentService, EmailMapper emailMapper) {
        this.userService = userService;
        this.emailService = emailService;
        this.attachmentService = attachmentService;
        this.emailMapper = emailMapper;
    }

    @GetMapping("/inbox")
    public String inbox(@AuthenticationPrincipal CustomUserDetails userDetails, 
                      @RequestParam(defaultValue = "0") int page,
                      @RequestParam(defaultValue = "10") int size,
                      Model model) {
        UUID userId = userDetails.getUser().getId();
        Page<Email> emails = emailService.getInboxEmails(userId, page, size);
        
        // Create a map of email IDs to a boolean indicating whether they have attachments
        Map<UUID, Boolean> hasAttachments = createHasAttachmentsMap(emails);
        model.addAttribute("hasAttachments", hasAttachments);
        
        // Add common data to model
        addCommonModelAttributes(model, emails, page, userId, "inbox");
        
        return "inbox";
    }

    @GetMapping("/sent")
    public String sent(@AuthenticationPrincipal CustomUserDetails userDetails,
                      @RequestParam(defaultValue = "0") int page,
                      @RequestParam(defaultValue = "10") int size,
                      Model model) {
        UUID userId = userDetails.getUser().getId();
        Page<Email> emails = emailService.getSentEmails(userId, page, size);
        
        // Create a map of email IDs to a boolean indicating whether they have attachments
        Map<UUID, Boolean> hasAttachments = createHasAttachmentsMap(emails);
        model.addAttribute("hasAttachments", hasAttachments);
        
        // Add common data to model
        addCommonModelAttributes(model, emails, page, userId, "sent");
        
        return "sent";
    }

    @GetMapping("/drafts")
    public String drafts(@AuthenticationPrincipal CustomUserDetails userDetails,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size,
                        Model model) {
        UUID userId = userDetails.getUser().getId();
        Page<Email> emails = emailService.getDraftEmails(userId, page, size);
        
        // Create a map of email IDs to a boolean indicating whether they have attachments
        Map<UUID, Boolean> hasAttachments = createHasAttachmentsMap(emails);
        model.addAttribute("hasAttachments", hasAttachments);
        
        // Add common data to model
        addCommonModelAttributes(model, emails, page, userId, "drafts");
        
        return "drafts";
    }

    @GetMapping("/trash")
    public String trash(@AuthenticationPrincipal CustomUserDetails userDetails,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       Model model) {
        UUID userId = userDetails.getUser().getId();
        Page<Email> emails = emailService.getTrashEmails(userId, page, size);
        
        // Create a map of email IDs to a boolean indicating whether they have attachments
        Map<UUID, Boolean> hasAttachments = createHasAttachmentsMap(emails);
        model.addAttribute("hasAttachments", hasAttachments);
        
        // Add common data to model
        addCommonModelAttributes(model, emails, page, userId, "trash");
        
        return "trash";
    }

    /**
     * Helper method to create a map of email IDs to a boolean indicating whether they have attachments
     */
    private Map<UUID, Boolean> createHasAttachmentsMap(Page<Email> emails) {
        Map<UUID, Boolean> hasAttachments = new HashMap<>();
        
        // Get a list of all email IDs
        List<UUID> emailIds = emails.getContent().stream()
                .map(Email::getId)
                .collect(Collectors.toList());
        
        // Use the attachment service to check which emails have attachments
        if (!emailIds.isEmpty()) {
            hasAttachments = attachmentService.checkEmailsHaveAttachments(emailIds);
        }
        
        return hasAttachments;
    }

    @GetMapping("/email/{id}")
    public String viewEmail(@PathVariable UUID id, 
                          @AuthenticationPrincipal CustomUserDetails userDetails,
                          Model model) {
        UUID userId = userDetails.getUser().getId();
        Optional<Email> optionalEmail = emailService.getEmailWithAttachments(id);
        
        if (optionalEmail.isEmpty()) {
            throw new ResourceNotFoundException("Email not found with id: " + id);
        }
        
        Email email = optionalEmail.get();
        
        // Security check: only sender or recipient can view the email
        if (!email.getSender().getId().equals(userId) && 
            !email.getRecipient().getId().equals(userId)) {
            throw new ResourceNotFoundException("Email not found with id: " + id);
        }
        
        // If this is the recipient viewing the email, mark as read
        if (email.getRecipient().getId().equals(userId) && !email.isRead()) {
            emailService.markAsRead(id, userId);
        }
        
        // Get sender and recipient details
        User sender = email.getSender();
        User recipient = email.getRecipient();
        
        // Convert to DTO for display
        EmailDTO emailDTO = emailMapper.toDto(email);
        
        model.addAttribute("email", email);
        model.addAttribute("emailDTO", emailDTO);
        model.addAttribute("sender", sender);
        model.addAttribute("recipient", recipient);
        model.addAttribute("isOwner", email.getSender().getId().equals(userId));
        model.addAttribute("hasAttachments", !email.getAttachments().isEmpty());
        model.addAttribute("attachments", email.getAttachments());
        model.addAttribute("unreadCount", emailService.countByRecipientIdAndReadFalseAndTrashFalse(userId));
        
        return "view-email";
    }

    @PostMapping("/email/{id}/read")
    public String markAsRead(@PathVariable UUID id,
                           @AuthenticationPrincipal CustomUserDetails userDetails,
                           @RequestHeader(value = "Referer", required = false) String referer,
                           RedirectAttributes redirectAttributes) {
        emailService.markAsRead(id, userDetails.getUser().getId());
        redirectAttributes.addFlashAttribute("message", "Email marked as read");
        return referer != null ? "redirect:" + referer : "redirect:/inbox";
    }

    @PostMapping("/email/{id}/unread")
    public String markAsUnread(@PathVariable UUID id,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             @RequestHeader(value = "Referer", required = false) String referer,
                             RedirectAttributes redirectAttributes) {
        emailService.markAsUnread(id);
        redirectAttributes.addFlashAttribute("message", "Email marked as unread");
        return referer != null ? "redirect:" + referer : "redirect:/inbox";
    }

    @PostMapping("/email/{id}/star")
    public String toggleStar(@PathVariable UUID id,
                           @AuthenticationPrincipal CustomUserDetails userDetails,
                           @RequestHeader(value = "Referer", required = false) String referer,
                           RedirectAttributes redirectAttributes) {
        emailService.toggleStar(id);
        redirectAttributes.addFlashAttribute("message", "Email status updated");
        return referer != null ? "redirect:" + referer : "redirect:/inbox";
    }

    @GetMapping("/diagnostics/email-check")
    public String checkEmailDelivery(@AuthenticationPrincipal CustomUserDetails userDetails,
                                   Model model) {
        UUID userId = userDetails.getUser().getId();
        
        // Get summary count information
        long totalSent = emailService.getSentEmails(userId, 0, 10).getTotalElements();
        long totalReceived = emailService.getInboxEmails(userId, 0, 10).getTotalElements();
        long totalUnread = emailService.countByRecipientIdAndReadFalseAndTrashFalse(userId);
        long totalDrafts = emailService.getDraftEmails(userId, 0, 10).getTotalElements();
        long totalTrash = emailService.getTrashEmails(userId, 0, 10).getTotalElements();
        
        model.addAttribute("totalSent", totalSent);
        model.addAttribute("totalReceived", totalReceived);
        model.addAttribute("totalUnread", totalUnread);
        model.addAttribute("totalDrafts", totalDrafts);
        model.addAttribute("totalTrash", totalTrash);
        model.addAttribute("unreadCount", totalUnread);
        
        return "email-diagnostics";
    }
    
    /**
     * Add common model attributes for all email list views
     */
    private void addCommonModelAttributes(Model model, Page<Email> emails, int page, UUID userId, String folderType) {
        model.addAttribute("emails", emails);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", emails.getTotalPages());
        model.addAttribute("totalItems", emails.getTotalElements());
        model.addAttribute("hasNext", emails.hasNext());
        model.addAttribute("hasPrevious", emails.hasPrevious());
        model.addAttribute("folderType", folderType);
        model.addAttribute("unreadCount", emailService.countByRecipientIdAndReadFalseAndTrashFalse(userId));
    }
} 