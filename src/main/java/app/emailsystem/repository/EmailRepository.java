package app.emailsystem.repository;

import app.emailsystem.entity.Email;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import java.util.List;

public interface EmailRepository extends JpaRepository<Email, UUID> {
    Page<Email> findByRecipientIdAndTrashFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    
    @Query("SELECT e FROM Email e JOIN FETCH e.sender WHERE e.recipient.id = :userId AND e.trash = false ORDER BY e.createdAt DESC")
    List<Email> findInboxEmailsWithSender(@Param("userId") UUID userId);
    
    Page<Email> findBySenderIdAndSentTrueAndTrashFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    Page<Email> findBySenderIdAndDraftTrueAndTrashFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    Page<Email> findByTrashTrueAndSenderIdOrTrashTrueAndRecipientIdOrderByCreatedAtDesc(UUID senderId, UUID recipientId, Pageable pageable);
    
    // Count unread emails
    long countByRecipientIdAndReadFalseAndTrashFalse(UUID userId);
} 