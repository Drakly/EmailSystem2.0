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
import app.emailsystem.mapper.EmailMapper;
import org.hibernate.Hibernate;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class EmailService {
    
    private final EmailRepository emailRepository;
    private final UserRepository userRepository;
    private final AttachmentService attachmentService;
    private final EmailMapper emailMapper;

    @Autowired
    public EmailService(EmailRepository emailRepository, UserRepository userRepository, AttachmentService attachmentService, EmailMapper emailMapper) {
        this.emailRepository = emailRepository;
        this.userRepository = userRepository;
        this.attachmentService = attachmentService;
        this.emailMapper = emailMapper;
    }

    private static final int PAGE_SIZE = 20;

    /**
     * Get inbox emails with eager loading of senders and recipients
     */
    @Transactional(readOnly = true)
    public Page<Email> getInboxEmails(UUID userId, int page, int size) {
        log.debug("Fetching inbox emails for user: {} with page size: {}", userId, size);
        
        try {
            Page<Email> emails = emailRepository.findByRecipientIdAndTrashFalseOrderByCreatedAtDesc(
                userId,
                PageRequest.of(page, size)
            );
            
            // Initialize sender and recipient for each email to avoid LazyInitializationExceptions
            for (Email email : emails.getContent()) {
                if (email.getSender() != null) {
                    Hibernate.initialize(email.getSender());
                }
                if (email.getRecipient() != null) {
                    Hibernate.initialize(email.getRecipient());
                }
            }
            
            log.debug("Found {} inbox emails (page {}, size {}) for user {}", 
                    emails.getContent().size(), page, size, userId);
            
            return emails;
        } catch (Exception e) {
            log.error("Error fetching inbox emails: {}", e.getMessage(), e);
            // Return empty page in case of error
            return Page.empty(PageRequest.of(page, size));
        }
    }

    /**
     * Find recipient user by email
     *
     * @param email the recipient email
     * @return the recipient user
     * @throws EmailSystemException if recipient not found
     */
    public User findRecipient(String email) {
        Optional<User> recipient = userRepository.findByEmail(email);
        if (recipient.isEmpty()) {
            throw new EmailSystemException("Recipient not found with email: " + email);
        }
        return recipient.get();
    }

    @Transactional
    public List<Email> sendEmail(UUID senderId, EmailDTO emailDTO) {
        log.info("Sending internal email from user {} to recipients {}", senderId, emailDTO.getRecipients());
        
        User sender = userRepository.findById(senderId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", senderId));
        
        // Parse recipients
        String[] recipientEmails = emailDTO.getRecipients().split(",");
        List<Email> savedEmails = new ArrayList<>();
        
        // Process each recipient
        for (String recipientEmail : recipientEmails) {
            String email = recipientEmail.trim();
            if (!email.isEmpty()) {
                try {
                    // Find recipient user
                    User recipient = findRecipient(email);
                    
                    // Use the mapper to create the email entity
                    Email emailEntity = emailMapper.toEntity(emailDTO, sender);
                    emailEntity.setRecipient(recipient);
                    
                    // Save to database
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
                } catch (ResourceNotFoundException e) {
                    log.warn("Recipient not found with email: {}", email);
                    // Continue with other recipients even if one is not found
                } catch (Exception e) {
                    log.error("Error saving email to recipient {}: {}", email, e.getMessage(), e);
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
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", senderId));
            
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
            
            // Use the mapper to create a draft email
            Email draft = emailMapper.toDraftEntity(emailDTO, sender);
                
            // Set recipient if provided
            if (emailDTO.getRecipients() != null && !emailDTO.getRecipients().isEmpty()) {
                String primaryRecipient = emailDTO.getRecipients().split(",")[0].trim();
                try {
                    User recipient = findRecipient(primaryRecipient);
                    draft.setRecipient(recipient);
                } catch (EmailSystemException e) {
                    // If recipient doesn't exist yet, we still save the draft without a recipient
                    log.info("Recipient email not found in system, saving draft without recipient: {}", primaryRecipient);
                    
                    // Set a temporary recipient as sender (we need a not-null recipient due to DB constraint)
                    // This will be replaced when the draft is sent
                    draft.setRecipient(sender);
                }
            } else {
                // Set a temporary recipient as sender (we need a not-null recipient due to DB constraint)
                draft.setRecipient(sender);
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

    @Transactional(readOnly = true)
    public Optional<Email> getEmailWithAttachments(UUID id) {
        log.debug("Getting email with attachments for id: {}", id);
        Optional<Email> emailOpt = emailRepository.findById(id);
        
        if (emailOpt.isPresent()) {
            Email email = emailOpt.get();
            // Load the attachments eagerly to avoid LazyInitializationException
            email.getAttachments().size();
            
            // Load sender and recipient to avoid LazyInitializationException
            if (email.getSender() != null) {
                email.getSender().getEmail(); // Touch to initialize
            }
            
            if (email.getRecipient() != null) {
                email.getRecipient().getEmail(); // Touch to initialize
            }
        }
        
        return emailOpt;
    }

    /**
     * Get sent emails with eager loading of senders and recipients
     */
    @Transactional(readOnly = true)
    public Page<Email> getSentEmails(UUID userId, int page, int size) {
        log.debug("Fetching sent emails for user: {}", userId);
        Page<Email> emails = emailRepository.findBySenderIdAndSentTrueAndTrashFalseOrderByCreatedAtDesc(
            userId, 
            PageRequest.of(page, size)
        );
        
        // Initialize recipient for each email to avoid LazyInitializationExceptions
        for (Email email : emails.getContent()) {
            if (email.getRecipient() != null) {
                Hibernate.initialize(email.getRecipient());
            }
        }
        
        return emails;
    }

    /**
     * Get draft emails with eager loading of recipients
     */
    @Transactional(readOnly = true)
    public Page<Email> getDraftEmails(UUID userId, int page, int size) {
        log.debug("Fetching draft emails for user: {}", userId);
        Page<Email> emails = emailRepository.findBySenderIdAndDraftTrueAndTrashFalseOrderByCreatedAtDesc(
            userId, 
            PageRequest.of(page, size)
        );
        
        // Initialize recipient for each email to avoid LazyInitializationExceptions
        for (Email email : emails.getContent()) {
            if (email.getRecipient() != null) {
                Hibernate.initialize(email.getRecipient());
            }
        }
        
        return emails;
    }

    /**
     * Get trash emails with eager loading of senders and recipients
     */
    @Transactional(readOnly = true)
    public Page<Email> getTrashEmails(UUID userId, int page, int size) {
        log.debug("Fetching trash emails for user: {}", userId);
        Page<Email> emails = emailRepository.findByTrashTrueAndSenderIdOrTrashTrueAndRecipientIdOrderByCreatedAtDesc(
            userId, userId, PageRequest.of(page, size)
        );
        
        // Initialize sender and recipient for each email to avoid LazyInitializationExceptions
        for (Email email : emails.getContent()) {
            if (email.getSender() != null) {
                Hibernate.initialize(email.getSender());
            }
            if (email.getRecipient() != null) {
                Hibernate.initialize(email.getRecipient());
            }
        }
        
        return emails;
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

    /**
     * Get inbox emails with sender eagerly loaded, limited by count
     */
    @Transactional(readOnly = true)
    public List<Email> getInboxEmailsWithSender(UUID userId, int limit) {
        log.debug("Fetching {} recent inbox emails with sender for user: {}", limit, userId);
        List<Email> emails = emailRepository.findInboxEmailsWithSender(userId);
        
        // Limit the number of emails returned
        if (emails.size() > limit) {
            emails = emails.subList(0, limit);
        }
        
        // Eagerly initialize the sender for each email to avoid LazyInitializationExceptions
        for (Email email : emails) {
            if (email.getSender() != null) {
                Hibernate.initialize(email.getSender());
            }
        }
        
        return emails;
    }

    /**
     * Check if emails have attachments and return a map of email IDs to attachment status
     */
    @Transactional(readOnly = true)
    public Map<UUID, Boolean> checkEmailsHaveAttachments(List<Email> emails) {
        Map<UUID, Boolean> hasAttachments = new HashMap<>();
        
        if (emails == null || emails.isEmpty()) {
            return hasAttachments;
        }
        
        // Extract IDs from emails
        List<UUID> emailIds = emails.stream()
            .map(Email::getId)
            .collect(Collectors.toList());
            
        // Use attachmentService to check which emails have attachments
        return attachmentService.checkEmailsHaveAttachments(emailIds);
    }
} 