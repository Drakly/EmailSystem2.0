package app.emailsystem.service;

import app.emailsystem.dto.EmailDTO;
import app.emailsystem.entity.Email;
import app.emailsystem.entity.User;
import app.emailsystem.exception.EmailSystemException;
import app.emailsystem.exception.ResourceNotFoundException;
import app.emailsystem.repository.EmailRepository;
import app.emailsystem.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
@Slf4j
@Service
@Transactional
public class EmailService {
    
    private final EmailRepository emailRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    @Autowired
    public EmailService(EmailRepository emailRepository, UserRepository userRepository, JavaMailSender mailSender) {
        this.emailRepository = emailRepository;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
    }

    private static final int PAGE_SIZE = 20;

    @Transactional(readOnly = true)
    public Page<Email> getInboxEmails(UUID userId, int page) {
        log.debug("Fetching inbox emails for user: {}", userId);
        return emailRepository.findByRecipientIdAndTrashFalseOrderByCreatedAtDesc(
            userId, 
            PageRequest.of(page, PAGE_SIZE)
        );
    }

    @Transactional
    public Email sendEmail(UUID senderId, UUID recipientId, String subject, String content) {
        log.info("Sending email from user {} to user {}", senderId, recipientId);
        
        User sender = userRepository.findById(senderId)
            .orElseThrow(() -> new ResourceNotFoundException("Sender not found with id: " + senderId));
        
        User recipient = userRepository.findById(recipientId)
            .orElseThrow(() -> new ResourceNotFoundException("Recipient not found with id: " + recipientId));

        try {
            // Create and send email
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            
            helper.setFrom(sender.getEmail());
            helper.setTo(recipient.getEmail());
            helper.setSubject(subject);
            helper.setText(content, true);
            
            mailSender.send(message);

            // Save email to database
            Email email = new Email();
            email.setSender(sender);
            email.setRecipient(recipient);
            email.setSubject(subject);
            email.setContent(content);
            email.setSent(true);

            return emailRepository.save(email);
        } catch (MessagingException e) {
            log.error("Failed to send email: {}", e.getMessage());
            throw new EmailSystemException("Failed to send email: " + e.getMessage());
        }
    }

    public Email saveDraft(EmailDTO emailDTO, UUID senderId) {
        log.info("Saving draft for user: {}", senderId);
        try {
            User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found with id: " + senderId));
            
            Email draft = Email.builder()
                .sender(sender)
                .subject(emailDTO.getSubject())
                .content(emailDTO.getContent())
                .createdAt(LocalDateTime.now())
                .read(false)
                .sent(false)
                .build();
                
            if (emailDTO.getRecipients() != null && !emailDTO.getRecipients().isEmpty()) {
                Set<Optional<User>> recipients = Arrays.stream(emailDTO.getRecipients().split(","))
                    .map(String::trim)
                    .map(userRepository::findByEmail)
                    .collect(Collectors.toSet());
                draft.setRecipient(recipients.iterator().next().orElseThrow(() -> new ResourceNotFoundException("Recipient not found with email: " + emailDTO.getRecipients())));
            }
            
            return emailRepository.save(draft);
        } catch (Exception e) {
            log.error("Failed to save draft", e);
            throw new EmailSystemException("Failed to save draft: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Optional<Email> getEmail(UUID id) {
        log.debug("Getting email with id: {}", id);
        return emailRepository.findById(id);
    }

    public Page<Email> getSentEmails(UUID userId, int page) {
        log.debug("Fetching sent emails for user: {}", userId);
        return emailRepository.findBySenderIdAndDraftFalseAndTrashFalseOrderByCreatedAtDesc(
            userId,
            PageRequest.of(page, PAGE_SIZE)
        );
    }

    public Page<Email> getDraftEmails(UUID userId, int page) {
        log.debug("Fetching draft emails for user: {}", userId);
        return emailRepository.findBySenderIdAndDraftTrueOrderByCreatedAtDesc(
            userId,
            PageRequest.of(page, PAGE_SIZE)
        );
    }

    public Page<Email> getTrashEmails(UUID userId, int page) {
        log.debug("Fetching trash emails for user: {}", userId);
        return emailRepository.findByTrashTrueAndSenderIdOrderByCreatedAtDesc(
            userId,
            PageRequest.of(page, PAGE_SIZE)
        );
    }

    public void moveToTrash(UUID emailId) {
        log.info("Moving email to trash, id: {}", emailId);
        Email email = getEmailById(emailId);
        email.setTrash(true);
        emailRepository.save(email);
    }

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

    public void markAsUnread(UUID emailId) {
        log.info("Marking email as unread, id: {}", emailId);
        Email email = getEmailById(emailId);
        email.setRead(false);
        emailRepository.save(email);
    }

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

    private Email getEmailById(UUID id) {
        log.debug("Fetching email with id: {}", id);
        return emailRepository.findById(id)
            .orElseThrow(() -> new EmailSystemException("Email not found with id: " + id));
    }
} 