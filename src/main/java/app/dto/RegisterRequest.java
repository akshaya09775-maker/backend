package app.dto;

import app.entity.User;
import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank(message = "Name is required") @Size(max = 100, message = "Name is too long") String name,
        @NotBlank(message = "A valid email is required") @Email(message = "A valid email is required")
        @Size(max = 150, message = "Email is too long") String email,
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain a letter and a number")
        String password,
        @NotNull(message = "Role is required") User.Role role,
        @NotNull(message = "You must accept the Terms of Service and Privacy Policy")
        @AssertTrue(message = "You must accept the Terms of Service and Privacy Policy") Boolean acceptTerms) {}
