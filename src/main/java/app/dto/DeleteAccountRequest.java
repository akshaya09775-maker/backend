package app.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(@NotBlank(message = "Please confirm with your password") String password) {}
