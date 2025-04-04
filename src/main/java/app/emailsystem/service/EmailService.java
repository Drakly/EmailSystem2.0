package app.emailsystem.service;

import app.emailsystem.dto.EmailDTO;
import app.emailsystem.entity.Attachment;
import app.emailsystem.entity.Email;
import app.emailsystem.entity.User;
import app.emailsystem.exception.EmailSystemException;
import app.emailsystem.exception.ResourceNotFoundException;
import app.emailsystem.repository.EmailRepository;
import app.emailsystem.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@Transactional
public class EmailService {
    
    private final EmailRepository emailRepository;
    private final UserRepository userRepository;
    private final AttachmentService attachmentService;

    @Autowired
    public EmailService(EmailRepository emailRepository, UserRepository userRepository, AttachmentService attachmentService) {
        this.emailRepository = emailRepository;
        this.userRepository = userRepository;
        this.attachmentService = attachmentService;
    }

    private static final int PAGE_SIZE = 20;

    @Transactional(readOnly = true)
    public Page<Email> getInboxEmails(UUID userId, int page) {
        log.debug("Fetching inbox emails for user: {}", userId);
        Page<Email> emails = emailRepository.findByRecipientIdAndTrashFalseOrderByCreatedAtDesc(
            userId, 
            PageRequest.of(page, PAGE_SIZE)
        );
        log.debug("Found {} inbox emails for user {}", emails.getContent().size(), userId);
        return emails;
    }

    @Transactional
    public List<Email> sendEmail(UUID senderId, EmailDTO emailDTO) {
        log.info("Sending internal email from user {} to recipients {}", senderId, emailDTO.getRecipients());
        
        User sender = userRepository.findById(senderId)
            .orElseThrow(() -> new ResourceNotFoundException("Sender not found with id: " + senderId));
        
        // Parse recipients
        String[] recipientEmails = emailDTO.getRecipients().split(",");
        List<Email> savedEmails = new ArrayList<>();
        
        // Process each recipient
        for (String recipientEmail : recipientEmails) {
            String email = recipientEmail.trim();
            if (!email.isEmpty()) {
                Optional<User> recipientOpt = userRepository.findByEmail(email);
                
                if (recipientOpt.isPresent()) {
                    User recipient = recipientOpt.get();
                    
                    try {
                        // Save to database
                        Email emailEntity = Email.builder()
                            .sender(sender)
                            .recipient(recipient)
                            .subject(emailDTO.getSubject())
                            .content(emailDTO.getContent())
                            .createdAt(LocalDateTime.now())
                            .read(false)
                            .sent(true)
                            .draft(false)
                            .trash(false)
                            .starred(false)
                            .build();
                        
                        // Double-check createdAt is set before saving
                        if (emailEntity.getCreatedAt() == null) {
                            emailEntity.setCreatedAt(LocalDateTime.now());
                        }
                        
                        Email savedEmail = emailRepository.save(emailEntity);
                        log.debug("Email saved with ID: {}, Sender: {}, Recipient: {}", 
                            savedEmail.getId(), savedEmail.getSender().getEmail(), savedEmail.getRecipient().getEmail());
                        
                        // Save attachments if any
                        if (emailDTO.getAttachments() != null && !emailDTO.getAttachments().isEmpty()) {
                            attachmentService.saveAttachments(savedEmail, emailDTO.getAttachments());
                        }
                        
                        // Force a flush to ensure the email is saved to the database
                        emailRepository.flush();
                        
                        savedEmails.add(savedEmail);
                        log.info("Email saved successfully for recipient: {}", email);
                    } catch (Exception e) {
                        log.error("Error saving email to recipient {}: {}", email, e.getMessage(), e);
                    }
                } else {
                    log.warn("Recipient not found with email: {}", email);
                    // Continue with other recipients even if one is not found
                }
            }
        }
        
        // Return error if no valid recipients
        if (savedEmails.isEmpty()) {
            log.warn("No valid recipients found, email not sent");
            throw new EmailSystemException("No valid system users found with the provided email addresses");
        }
        
        // Log the number of emails that were sent
        log.info("Successfully sent {} emails", savedEmails.size());
        return savedEmails;
    }

    @Transactional
    public Email saveDraft(EmailDTO emailDTO, UUID senderId) {
        log.info("Saving draft for user: {}", senderId);
        try {
            User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found with id: " + senderId));
            
            // If we're updating an existing draft
            Email existingDraft = null;
            if (emailDTO.getId() != null && !emailDTO.getId().isEmpty()) {
                try {
                    UUID draftId = UUID.fromString(emailDTO.getId());
                    Optional<Email> existingDraftOpt = emailRepository.findById(draftId);
                    
                    if (existingDraftOpt.isPresent() && existingDraftOpt.get().isDraft()) {
                        existingDraft = existingDraftOpt.get();
                        log.debug("Updating existing draft with ID: {}", draftId);
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid draft ID format: {}", emailDTO.getId());
                }
            }
            
            // Build the base draft email
            Email.EmailBuilder draftBuilder = Email.builder()
                .sender(sender)
                .subject(emailDTO.getSubject() != null ? emailDTO.getSubject() : "")
                .content(emailDTO.getContent() != null ? emailDTO.getContent() : "")
                .createdAt(LocalDateTime.now())
                .read(false)
                .sent(false)
                .draft(true)
                .trash(false)
                .starred(false);
                
            // Set recipient if provided
            if (emailDTO.getRecipients() != null && !emailDTO.getRecipients().isEmpty()) {
                String primaryRecipient = emailDTO.getRecipients().split(",")[0].trim();
                Optional<User> recipientOpt = userRepository.findByEmail(primaryRecipient);
                
                if (recipientOpt.isPresent()) {
                    draftBuilder.recipient(recipientOpt.get());
                } else {
                    // If recipient doesn't exist yet, we still save the draft without a recipient
                    log.info("Recipient email not found in system, saving draft without recipient: {}", primaryRecipient);
                    
                    // Set a temporary recipient as sender (we need a not-null recipient due to DB constraint)
                    // This will be replaced when the draft is sent
                    draftBuilder.recipient(sender);
                }
            } else {
                // Set a temporary recipient as sender (we need a not-null recipient due to DB constraint)
                draftBuilder.recipient(sender);
            }
            
            Email draft;
            
            // If updating existing draft, update its values
            if (existingDraft != null) {
                existingDraft.setSubject(emailDTO.getSubject() != null ? emailDTO.getSubject() : "");
                existingDraft.setContent(emailDTO.getContent() != null ? emailDTO.getContent() : "");
                
                // Update recipient if changed
                if (emailDTO.getRecipients() != null && !emailDTO.getRecipients().isEmpty()) {
                    String primaryRecipient = emailDTO.getRecipients().split(",")[0].trim();
                    Optional<User> recipientOpt = userRepository.findByEmail(primaryRecipient);
                    
                    if (recipientOpt.isPresent()) {
                        existingDraft.setRecipient(recipientOpt.get());
                    }
                }
                
                draft = existingDraft;
            } else {
                draft = draftBuilder.build();
            }
            
            // Double-check createdAt is set before saving
            if (draft.getCreatedAt() == null) {
                draft.setCreatedAt(LocalDateTime.now());
            }
            
            Email savedDraft = emailRepository.save(draft);
            
            // Process attachmentIds (existing attachments to keep)
            if (emailDTO.getAttachmentIds() != null && !emailDTO.getAttachmentIds().isEmpty()) {
                log.debug("Processing existing attachment IDs: {}", emailDTO.getAttachmentIds());
                // Logic for handling existing attachments if needed
            }
            
            // Save new attachments if any
            if (emailDTO.getAttachments() != null && !emailDTO.getAttachments().isEmpty()) {
                log.debug("Processing {} new attachments", emailDTO.getAttachments().size());
                attachmentService.saveAttachments(savedDraft, emailDTO.getAttachments());
            }
            
            // Force a flush to ensure the draft is saved to the database
            emailRepository.flush();
            
            return savedDraft;
        } catch (Exception e) {
            log.error("Failed to save draft", e);
            throw new EmailSystemException("Failed to save draft: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Optional<Email> getEmail(UUID id) {
        log.debug("Getting email with id: {}", id);
        Optional<Email> emailOpt = emailRepository.findById(id);
        
        if (emailOpt.isPresent()) {
            Email email = emailOpt.get();
            // Load the attachments to avoid LazyInitializationException
            email.getAttachments().size();
        }
        
        return emailOpt;
    }

    public Page<Email> getSentEmails(UUID userId, int page) {
        log.debug("Fetching sent emails for user: {}", userId);
        Page<Email> emails = emailRepository.findBySenderIdAndSentTrueAndTrashFalseOrderByCreatedAtDesc(
            userId,
            PageRequest.of(page, PAGE_SIZE)
        );
        log.debug("Found {} sent emails for user {}", emails.getContent().size(), userId);
        return emails;
    }

    public Page<Email> getDraftEmails(UUID userId, int page) {
        log.debug("Fetching draft emails for user: {}", userId);
        return emailRepository.findBySenderIdAndDraftTrueAndTrashFalseOrderByCreatedAtDesc(
            userId,
            PageRequest.of(page, PAGE_SIZE)
        );
    }

    public Page<Email> getTrashEmails(UUID userId, int page) {
        log.debug("Fetching trash emails for user: {}", userId);
        return emailRepository.findByTrashTrueAndSenderIdOrTrashTrueAndRecipientIdOrderByCreatedAtDesc(
            userId,
            userId,
            PageRequest.of(page, PAGE_SIZE)
        );
    }

    @Transactional
    public void moveToTrash(UUID emailId) {
        log.info("Moving email to trash, id: {}", emailId);
        Email email = getEmailById(emailId);
        email.setTrash(true);
        emailRepository.save(email);
    }

    @Transactional
    public void restoreFromTrash(UUID emailId) {
        log.info("Restoring email from trash, id: {}", emailId);
        Email email = getEmailById(emailId);
        email.setTrash(false);
        emailRepository.save(email);
    }

    @Transactional
    public Email markAsRead(UUID id, UUID userId) {
        log.info("Marking email {} as read for user: {}", id, userId);
        Email email = emailRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Email not found with id: " + id));

        if (!email.getRecipient().getId().equals(userId)) {
            throw new EmailSystemException("You don't have permission to mark this email as read");
        }

        email.setRead(true);
        return emailRepository.save(email);
    }

    @Transactional
    public void markAsUnread(UUID emailId) {
        log.info("Marking email as unread, id: {}", emailId);
        Email email = getEmailById(emailId);
        email.setRead(false);
        emailRepository.save(email);
    }

    @Transactional
    public void toggleStar(UUID emailId) {
        log.info("Toggling star for email, id: {}", emailId);
        Email email = getEmailById(emailId);
        email.setStarred(!email.isStarred());
        emailRepository.save(email);
    }

    @Transactional
    public void deleteEmail(UUID id, UUID userId) {
        log.info("Deleting email with id: {} for user: {}", id, userId);
        Email email = emailRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Email not found with id: " + id));

        if (!email.getRecipient().getId().equals(userId) && !email.getSender().getId().equals(userId)) {
            throw new EmailSystemException("You don't have permission to delete this email");
        }

        emailRepository.delete(email);
    }

    @Transactional
    public void permanentlyDeleteEmail(UUID id, UUID userId) {
        log.info("Permanently deleting email with id: {} for user: {}", id, userId);
        Email email = emailRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Email not found with id: " + id));

        if (!email.getRecipient().getId().equals(userId) && !email.getSender().getId().equals(userId)) {
            throw new EmailSystemException("You don't have permission to delete this email");
        }
        
        if (!email.isTrash()) {
            throw new EmailSystemException("Email must be in trash to be permanently deleted");
        }

        emailRepository.delete(email);
    }

    private Email getEmailById(UUID id) {
        log.debug("Fetching email with id: {}", id);
        return emailRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Email not found with id: " + id));
    }
    
    /**
     * Count unread emails for a user
     * 
     * @param userId the user ID
     * @return the number of unread emails
     */
    public long countByRecipientIdAndReadFalseAndTrashFalse(UUID userId) {
        log.debug("Counting unread emails for user: {}", userId);
        return emailRepository.countByRecipientIdAndReadFalseAndTrashFalse(userId);
    }
    
    /**
     * Empty the trash for a user by permanently deleting all emails
     * 
     * @param userId the user ID
     * @return the number of emails deleted
     */
    @Transactional
    public int emptyTrash(UUID userId) {
        log.info("Emptying trash for user: {}", userId);
        
        // Find all emails in trash for this user (either as sender or recipient)
        Page<Email> trashEmails = emailRepository.findByTrashTrueAndSenderIdOrTrashTrueAndRecipientIdOrderByCreatedAtDesc(
            userId, userId, PageRequest.of(0, Integer.MAX_VALUE));
        
        int count = 0;
        for (Email email : trashEmails.getContent()) {
            emailRepository.delete(email);
            count++;
        }
        
        log.info("Deleted {} emails from trash for user: {}", count, userId);
        return count;
    }
} 