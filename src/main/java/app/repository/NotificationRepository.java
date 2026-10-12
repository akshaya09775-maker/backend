package app.repository;

import app.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
    long countByUserIdAndSeenFalse(Long userId);
    List<Notification> findByUserIdAndSeenFalse(Long userId);

    @org.springframework.transaction.annotation.Transactional
    void deleteByUserId(Long userId);
}
