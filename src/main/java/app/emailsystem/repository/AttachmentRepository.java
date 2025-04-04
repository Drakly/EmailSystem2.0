package app.emailsystem.repository;

import app.emailsystem.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    List<Attachment> findByEmailId(UUID emailId);
} 