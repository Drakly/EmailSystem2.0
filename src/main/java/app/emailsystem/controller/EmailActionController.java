package app.emailsystem.controller;

import app.emailsystem.service.EmailService;
import app.emailsystem.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Controller responsible for email actions such as moving to trash,
 * permanently deleting emails, and bulk operations
 */
@Controller
public class EmailActionController {

    private final EmailService emailService;
    private static final Logger log = LoggerFactory.getLogger(EmailActionController.class);

    @Autowired
    public EmailActionController(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping("/email/{id}/trash")
    public String moveToTrash(@PathVariable UUID id,
                           @RequestHeader(value = "Referer", required = false) String referer,
                           RedirectAttributes redirectAttributes) {
        try {
            emailService.moveToTrash(id);
            redirectAttributes.addFlashAttribute("message", "Email moved to trash");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Could not move email to trash: " + e.getMessage());
        }
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

    @PostMapping("/email/{id}/restore")
    public String restoreFromTrash(@PathVariable UUID id,
                                 RedirectAttributes redirectAttributes) {
        try {
            emailService.restoreFromTrash(id);
            redirectAttributes.addFlashAttribute("message", "Email restored from trash");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Could not restore email: " + e.getMessage());
        }
        return "redirect:/trash";
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
                    log.error("Error moving email to trash: {}", e.getMessage());
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