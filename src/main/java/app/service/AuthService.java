package app.service;

import app.dto.AuthResponse;
import app.dto.LoginRequest;
import app.dto.RegisterRequest;
import app.dto.UserResponse;
import app.entity.JobSeekerProfile;
import app.entity.RecruiterProfile;
import app.entity.User;
import app.repository.JobSeekerRepository;
import app.repository.RecruiterRepository;
import app.repository.UserRepository;
import app.security.JwtService;
import app.security.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s\"\\\\]+@[^@\\s\"\\\\]+\\.[^@\\s\"\\\\]+$");

    private final UserRepository userRepository;
    private final JobSeekerRepository seekerRepository;
    private final RecruiterRepository recruiterRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttempts;
    private final EmailService emailService;
    private final AuditService audit;

    public AuthResponse register(RegisterRequest req) {
        if (req.name() == null || req.name().isBlank()) bad("Name is required");
        if (req.email() == null || !EMAIL.matcher(req.email().trim()).matches()) bad("A valid email is required");
        if (req.password() == null || req.password().length() < 8) bad("Password must be at least 8 characters");
        if (req.role() == null) bad("Role is required");
        if (!Boolean.TRUE.equals(req.acceptTerms())) bad("You must accept the Terms of Service and Privacy Policy");
        if (req.role() == User.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin accounts cannot be self-registered");
        }

        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        User user = User.builder()
                .name(req.name().trim())
                .email(email)
                .password(passwordEncoder.encode(req.password()))
                .role(req.role())
                .termsAcceptedAt(java.time.LocalDateTime.now())
                .build();
        user = userRepository.save(user);
        if (user.getRole() == User.Role.RECRUITER) {
            recruiterRepository.save(RecruiterProfile.builder().user(user).build());
        } else {
            seekerRepository.save(JobSeekerProfile.builder().user(user).build());
        }
        audit.recordAs(email, "USER_REGISTERED", "user#" + user.getId(), user.getRole().name());
        emailService.sendWelcome(user.getEmail(), user.getName());
        return toAuth(user);
    }

    public AuthResponse login(LoginRequest req) {
        if (req.email() == null || req.password() == null) bad("Email and password are required");
        String email = req.email().trim().toLowerCase();
        if (loginAttempts.isLocked(email)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed attempts. Please try again later");
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !passwordEncoder.matches(req.password(), user.getPassword())) {
            loginAttempts.failed(email);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        loginAttempts.succeeded(email);
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account has been disabled by an administrator");
        }
        return toAuth(user);
    }

    private AuthResponse toAuth(User user) {
        return new AuthResponse(jwtService.generateToken(user.getEmail(), user.getRole().name()),
                UserResponse.from(user));
    }

    private static void bad(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
