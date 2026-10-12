package app.controller;

import app.dto.*;
import app.dto.LoginRequest;
import app.dto.RegisterRequest;
import app.dto.UserResponse;
import app.security.CurrentUser;
import app.service.AccountService;
import app.service.AuthService;
import app.service.ApplicationService;
import app.service.NotificationService;
import app.service.ProfileService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.ResponseEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;
    private final AccountService accountService;
    private final ProfileService profileService;
    private final ApplicationService applicationService;
    private final NotificationService notificationService;

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @GetMapping("/me")
    public UserResponse me() {
        return UserResponse.from(currentUser.get());
    }

    /** Always answers the same way so nobody can find out which emails are registered. */
    @PostMapping("/forgot-password")
    public Map<String, String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        accountService.requestPasswordReset(req.email());
        return Map.of("message", "If an account exists for that email, a reset link has been sent");
    }

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        accountService.resetPassword(req.token(), req.password());
        return Map.of("message", "Your password has been updated. Please sign in");
    }

    @PostMapping("/change-password")
    public Map<String, String> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        accountService.changePassword(currentUser.get(), req);
        return Map.of("message", "Password changed. Please sign in again");
    }

    @PutMapping("/me/preferences")
    public UserResponse preferences(@Valid @RequestBody PreferencesRequest req) {
        return UserResponse.from(accountService.setEmailNotifications(currentUser.get(), req.emailNotifications()));
    }

    /** Permanent deletion; POST (not DELETE) so the password can travel in the request body. */
    @PostMapping("/delete-account")
    public Map<String, String> deleteAccount(@Valid @RequestBody DeleteAccountRequest req) {
        accountService.deleteAccount(currentUser.get(), req.password());
        return Map.of("message", "Your account has been deleted");
    }

    /** "Download my data": everything the service holds about the logged-in user, as a JSON file. */
    @GetMapping("/me/export")
    public ResponseEntity<Map<String, Object>> exportData() {
        var user = currentUser.get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("account", UserResponse.from(user));
        if (user.getRole() == app.entity.User.Role.JOB_SEEKER) {
            data.put("profile", profileService.getSeeker(user));
            data.put("applications", applicationService.mine(user));
        }
        data.put("notifications", notificationService.list(user));
        return ResponseEntity.ok()
                .header("Content-Disposition", ContentDisposition.attachment().filename("my-data.json").build().toString())
                .body(data);
    }
}
