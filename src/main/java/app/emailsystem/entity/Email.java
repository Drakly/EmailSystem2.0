package app.emailsystem.entity;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "emails")
@Builder
public class Email {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Column(nullable = false)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "is_sent", nullable = false)
    private boolean sent;

    @Column(name = "is_draft", nullable = false)
    private boolean draft;

    @Column(name = "is_trash", nullable = false)
    private boolean trash;

    @Column(name = "is_starred", nullable = false)
    private boolean starred;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        read = false;
        sent = false;
        draft = false;
        trash = false;
        starred = false;
    }
} 