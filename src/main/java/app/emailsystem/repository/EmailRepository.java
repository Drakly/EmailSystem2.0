package app.emailsystem.repository;

import app.emailsystem.entity.Email;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface EmailRepository extends JpaRepository<Email, UUID> {
    Page<Email> findByRecipientIdAndTrashFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    Page<Email> findBySenderIdAndDraftFalseAndTrashFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    Page<Email> findBySenderIdAndDraftTrueOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    Page<Email> findByTrashTrueAndSenderIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
} 