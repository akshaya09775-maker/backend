package app.service;

import app.dto.ChangePasswordRequest;
import app.entity.*;
import app.repository.*;
import app.security.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Password reset / change, email preferences and permanent account deletion. */
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final java.util.regex.Pattern PASSWORD_RULE =
            java.util.regex.Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,72}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final JobSeekerRepository seekerRepository;
    private final RecruiterRepository recruiterRepository;
    private final ApplicationRepository applicationRepository;
    private final SavedJobRepository savedJobRepository;
    private final CompanyFollowRepository followRepository;
    private final NotificationRepository notificationRepository;
    private final JobRepository jobRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttempts;
    private final FileStorageService storage;
    private final EmailService email;
    private final AuditService audit;

    @Value("${app.password-reset.expiry-minutes:60}")
    private long resetExpiryMinutes;

    /** Always behaves the same whether or not the email exists, so accounts cannot be discovered. */
    @Transactional
    public void requestPasswordReset(String rawEmail) {
        String addr = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
        userRepository.findByEmail(addr).filter(User::isEnabled).ifPresent(user -> {
            tokenRepository.deleteByUserId(user.getId());
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            tokenRepository.save(PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(hash(token))
                    .expiresAt(LocalDateTime.now().plusMinutes(resetExpiryMinutes))
                    .build());
            email.sendPasswordReset(user.getEmail(), user.getName(), token);
            audit.recordAs(user.getEmail(), "PASSWORD_RESET_REQUESTED", user.getEmail(), null);
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken t = tokenRepository.findByTokenHash(hash(token == null ? "" : token.trim()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "This reset link is invalid or has expired"));
        if (t.getUsedAt() != null || t.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This reset link is invalid or has expired");
        }
        User user = t.getUser();
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account has been disabled");
        }
        checkPasswordRule(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);
        tokenRepository.deleteByUserId(user.getId());
        loginAttempts.succeeded(user.getEmail());
        audit.recordAs(user.getEmail(), "PASSWORD_RESET_COMPLETED", user.getEmail(), null);
        email.sendPasswordChanged(user.getEmail(), user.getName());
    }

    @Transactional
    public void changePassword(User user, ChangePasswordRequest req) {
        if (!passwordEncoder.matches(req.currentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        checkPasswordRule(req.newPassword());
        user.setPassword(passwordEncoder.encode(req.newPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);
        audit.recordAs(user.getEmail(), "PASSWORD_CHANGED", user.getEmail(), null);
        email.sendPasswordChanged(user.getEmail(), user.getName());
    }

    @Transactional
    public User setEmailNotifications(User user, boolean enabled) {
        user.setEmailNotifications(enabled);
        return userRepository.save(user);
    }

    /**
     * Permanently removes a job seeker and all their data. Recruiters are anonymised and their jobs
     * closed, because other people's applications and company pages depend on that data.
     */
    @Transactional
    public void deleteAccount(User user, String password) {
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is incorrect");
        }
        if (user.getRole() == User.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Administrator accounts cannot be deleted here");
        }
        String originalEmail = user.getEmail();
        String name = user.getName();
        Long id = user.getId();

        tokenRepository.deleteByUserId(id);
        notificationRepository.deleteByUserId(id);
        List<SavedJob> saved = savedJobRepository.findByUserIdOrderBySavedAtDesc(id);
        savedJobRepository.deleteAll(saved);
        List<CompanyFollow> follows = followRepository.findByUserIdOrderByFollowedAtDesc(id);
        followRepository.deleteAll(follows);

        if (user.getRole() == User.Role.JOB_SEEKER) {
            seekerRepository.findByUserId(id).ifPresent(profile -> {
                applicationRepository.deleteAll(applicationRepository.findBySeekerIdOrderByAppliedAtDesc(profile.getId()));
                storage.delete(profile.getResumeFileName());
                String avatar = profile.getAvatarUrl();
                String prefix = "/api/seeker/avatar/";
                if (avatar != null && avatar.startsWith(prefix)) storage.deleteImage(avatar.substring(prefix.length()));
                seekerRepository.delete(profile);
            });
            userRepository.delete(user);
        } else {
            recruiterRepository.findByUserId(id).ifPresent(profile -> {
                for (Job j : jobRepository.findByRecruiterIdOrderByCreatedAtDesc(profile.getId())) {
                    j.setActive(false);
                    j.setStatus("CLOSED");
                    jobRepository.save(j);
                }
            });
            user.setName("Deleted user");
            user.setEmail("deleted-" + id + "-" + UUID.randomUUID().toString().substring(0, 8) + "@deleted.invalid");
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setEnabled(false);
            user.setEmailNotifications(false);
            userRepository.save(user);
        }
        audit.recordAs(originalEmail, "ACCOUNT_DELETED", "user#" + id, user.getRole().name());
        email.sendAccountDeleted(originalEmail, name);
    }

    private static void checkPasswordRule(String password) {
        if (password == null || !PASSWORD_RULE.matcher(password).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be 8 to 72 characters and contain a letter and a number");
        }
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
