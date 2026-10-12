package app.service;

import app.dto.NotificationResponse;
import app.entity.Notification;
import app.entity.User;
import app.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** In-app notifications (shown under the bell icon). */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final EmailService emailService;

    public void send(User user, String message, String link) {
        String text = message.length() > 480 ? message.substring(0, 480) : message;
        repository.save(Notification.builder().user(user).message(text).link(link).build());
        if (user.wantsEmails()) {
            emailService.sendNotification(user.getEmail(), user.getName(), text, link);
        }
    }

    public List<NotificationResponse> list(User user) {
        return repository.findTop50ByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(n -> new NotificationResponse(n.getId(), n.getMessage(), n.getLink(),
                        n.isSeen(), n.getCreatedAt()))
                .toList();
    }

    public long unreadCount(User user) {
        return repository.countByUserIdAndSeenFalse(user.getId());
    }

    @Transactional
    public void markAllRead(User user) {
        List<Notification> unread = repository.findByUserIdAndSeenFalse(user.getId());
        for (Notification n : unread) n.setSeen(true);
        repository.saveAll(unread);
    }
}
