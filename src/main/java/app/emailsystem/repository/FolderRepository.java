package app.emailsystem.repository;

import app.emailsystem.entity.Folder;
import app.emailsystem.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface FolderRepository extends JpaRepository<Folder, UUID> {
    List<Folder> findByUser(User user);
} 