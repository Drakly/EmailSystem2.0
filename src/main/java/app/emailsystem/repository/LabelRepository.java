package app.emailsystem.repository;

import app.emailsystem.entity.Label;
import app.emailsystem.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface LabelRepository extends JpaRepository<Label, UUID> {
    List<Label> findByUser(User user);
} 