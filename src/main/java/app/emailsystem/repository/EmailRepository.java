package app.emailsystem.repository;

import app.emailsystem.entity.Email;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface EmailRepository extends JpaRepository<Email, UUID> {
    Page<Email> findByRecipientIdAndTrashFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    Page<Email> findBySenderIdAndSentTrueAndTrashFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    Page<Email> findBySenderIdAndDraftTrueAndTrashFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    Page<Email> findByTrashTrueAndSenderIdOrTrashTrueAndRecipientIdOrderByCreatedAtDesc(UUID senderId, UUID recipientId, Pageable pageable);
    
    // Count unread emails
    long countByRecipientIdAndReadFalseAndTrashFalse(UUID userId);
} 