package app.controller;

import app.dto.NotificationResponse;
import app.security.CurrentUser;
import app.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final CurrentUser currentUser;
    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> list() {
        return notificationService.list(currentUser.get());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notificationService.unreadCount(currentUser.get()));
    }

    @PutMapping("/read-all")
    public void readAll() {
        notificationService.markAllRead(currentUser.get());
    }
}
