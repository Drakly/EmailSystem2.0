package app.emailsystem.repository;

import app.emailsystem.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    List<Attachment> findByEmailId(UUID emailId);
    
    /**
     * Find all email IDs in the given list that have attachments, with the count of attachments
     * 
     * @param emailIds the list of email IDs to check
     * @return list of email IDs with attachment counts as Object[]
     */
    @Query("SELECT a.email.id, COUNT(a) FROM Attachment a WHERE a.email.id IN :emailIds GROUP BY a.email.id")
    List<Object[]> findEmailIdsWithAttachmentCounts(@Param("emailIds") List<UUID> emailIds);
} 