package app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(@NotBlank(message = "Email is required")
                                    @Size(max = 150, message = "Email is too long") String email) {}
