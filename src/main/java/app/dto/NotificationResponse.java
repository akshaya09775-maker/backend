package app.dto;

import java.time.LocalDateTime;

public record NotificationResponse(Long id, String message, String link, boolean seen,
                                   LocalDateTime createdAt) {}
