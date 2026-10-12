package app.dto;

import app.entity.User;

import java.time.LocalDateTime;

public record UserResponse(Long id, String name, String email, User.Role role,
                           boolean enabled, LocalDateTime createdAt, boolean emailNotifications) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole(),
                u.isEnabled(), u.getCreatedAt(), u.wantsEmails());
    }
}
