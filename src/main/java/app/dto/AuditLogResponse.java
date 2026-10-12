package app.dto;

import app.entity.AuditLog;

import java.time.LocalDateTime;

public record AuditLogResponse(Long id, String actor, String action, String target, String detail,
                               LocalDateTime createdAt) {
    public static AuditLogResponse from(AuditLog a) {
        return new AuditLogResponse(a.getId(), a.getActor(), a.getAction(), a.getTarget(), a.getDetail(),
                a.getCreatedAt());
    }
}
