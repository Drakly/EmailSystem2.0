package app.emailsystem.controller;

import app.emailsystem.entity.Email;
import app.emailsystem.security.CustomUserDetails;
import app.emailsystem.service.EmailService;
import app.emailsystem.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Controller for the main dashboard
 */
@Controller
public class MainController {

    private final EmailService emailService;
    private final UserService userService;
    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    @Autowired
    public MainController(EmailService emailService, UserService userService) {
        this.emailService = emailService;
        this.userService = userService;
    }
    
    @GetMapping("/dashboard")
    public String showDashboard(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        try {
            UUID userId = userDetails.getUser().getId();
            
            // Get dashboard statistics
            long unreadCount = emailService.countByRecipientIdAndReadFalseAndTrashFalse(userId);
            Page<Email> sentEmails = emailService.getSentEmails(userId, 0, 10);
            Page<Email> draftEmails = emailService.getDraftEmails(userId, 0, 10);
            
            // Get recent emails with eager-loaded senders
            List<Email> recentEmails = emailService.getInboxEmailsWithSender(userId, 5);
            
            // Use service method to safely check for attachments within a transaction
            Map<UUID, Boolean> hasAttachments = emailService.checkEmailsHaveAttachments(recentEmails);
            
            // Add data to model
            model.addAttribute("username", userDetails.getUser().getFirstName() + " " + userDetails.getUser().getLastName());
            model.addAttribute("unreadCount", unreadCount);
            model.addAttribute("sentCount", sentEmails.getTotalElements());
            model.addAttribute("draftCount", draftEmails.getTotalElements());
            model.addAttribute("recentEmails", recentEmails);
            model.addAttribute("hasAttachments", hasAttachments);
            
            return "main";
        } catch (Exception e) {
            log.error("Error loading dashboard: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to load dashboard: " + e.getMessage());
            return "error";
        }
    }
} 