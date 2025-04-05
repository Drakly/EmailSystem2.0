package app.emailsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "attachments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attachment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "file_name", nullable = false)
    private String filename;
    
    @Column(name = "file_path", nullable = false)
    private String filePath;
    
    @Column(nullable = false)
    private String contentType;
    
    @Column(name = "file_size", nullable = false)
    private long size;
    
    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] data;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_id")
    private Email email;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        
        if (filePath == null && filename != null) {
            filePath = "/attachments/" + filename;
        }
    }
} 