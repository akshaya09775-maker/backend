package app.dto;

import jakarta.validation.constraints.NotNull;

public record PreferencesRequest(@NotNull(message = "Preference is required") Boolean emailNotifications) {}
