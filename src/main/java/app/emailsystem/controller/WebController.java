package app.emailsystem.controller;

import app.emailsystem.dto.EmailDTO;
import app.emailsystem.entity.Email;
import app.emailsystem.service.EmailService;
import app.emailsystem.service.UserService;
import app.emailsystem.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

@Controller
public class WebController {

    private final EmailService emailService;
    
    private final UserService userService;

    @Autowired
    public WebController(UserService userService, EmailService emailService) {
        this.userService = userService;
        this.emailService = emailService;
    }

   
    @GetMapping("/inbox")
    public String inbox(@AuthenticationPrincipal CustomUserDetails userDetails, 
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Page<Email> emails = emailService.getInboxEmails(userDetails.getUser().getId(), page);
        
        model.addAttribute("emails", emails);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", emails.getTotalPages());
        return "inbox";
    }

    @GetMapping("/compose")
    public String compose(Model model) {
        model.addAttribute("emailDTO", new EmailDTO());
        return "compose";
    }

    @PostMapping("/send")
    public String sendEmail(@Valid @ModelAttribute EmailDTO emailDTO,
                          BindingResult result,
                          @AuthenticationPrincipal CustomUserDetails userDetails,
                          RedirectAttributes redirectAttributes,
                          Model model) {
        if (result.hasErrors()) {
            model.addAttribute("pageTitle", "Compose Email");
            return "compose";
        }
        UUID senderId = userDetails.getUser().getId();
        UUID recipientId = userService.getUserByEmail(emailDTO.getRecipients()).get().getId();
        emailService.sendEmail(senderId, recipientId, emailDTO.getSubject(), emailDTO.getContent());
        redirectAttributes.addFlashAttribute("message", "Email sent successfully!");
        return "redirect:/sent";
    }

    @GetMapping("/sent")
    public String sent(@AuthenticationPrincipal CustomUserDetails userDetails,
                      @RequestParam(defaultValue = "0") int page,
                      Model model) {
        Page<Email> emails = emailService.getSentEmails(userDetails.getUser().getId(), page);
        
        model.addAttribute("emails", emails);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", emails.getTotalPages());
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
        return "drafts";
    }

    @PostMapping("/save-draft")
    public String saveDraft(@ModelAttribute EmailDTO emailDTO,
                           @AuthenticationPrincipal CustomUserDetails userDetails,
                           RedirectAttributes redirectAttributes) {
        emailService.saveDraft(emailDTO, userDetails.getUser().getId());
        redirectAttributes.addFlashAttribute("message", "Draft saved successfully!");
        return "redirect:/drafts";
    }

    @GetMapping("/trash")
    public String trash(@AuthenticationPrincipal CustomUserDetails userDetails,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Page<Email> emails = emailService.getTrashEmails(userDetails.getUser().getId(), page);
        
        model.addAttribute("emails", emails);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", emails.getTotalPages());
        return "trash";
    }

    @PostMapping("/email/{id}/trash")
    public String moveToTrash(@PathVariable UUID id,
                            RedirectAttributes redirectAttributes) {
        emailService.moveToTrash(id);
        redirectAttributes.addFlashAttribute("message", "Email moved to trash");
        return "redirect:/inbox";
    }

    @GetMapping("/email/{id}")
    public String viewEmail(@PathVariable UUID id, Model model) {
        Optional<Email> email = emailService.getEmail(id);
        model.addAttribute("email", email);
        model.addAttribute("pageTitle", email.get().getSubject());
        return "view-email";
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
                           RedirectAttributes redirectAttributes) {
        emailService.markAsRead(id, userService.getUserById(id).get().getId());
        redirectAttributes.addFlashAttribute("message", "Email marked as read");
        return "redirect:/inbox";
    }

    @PostMapping("/email/{id}/unread")
    public String markAsUnread(@PathVariable UUID id,
                             RedirectAttributes redirectAttributes) {
        emailService.markAsUnread(id);
        redirectAttributes.addFlashAttribute("message", "Email marked as unread");
        return "redirect:/inbox";
    }

    @PostMapping("/email/{id}/star")
    public String toggleStar(@PathVariable UUID id,
                           RedirectAttributes redirectAttributes) {
        emailService.toggleStar(id);
        redirectAttributes.addFlashAttribute("message", "Email star toggled");
        return "redirect:/inbox";
    }
} 