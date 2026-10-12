package app.service;

import app.dto.RecruiterAdminResponse;
import app.dto.ReportResponse;
import app.dto.UserResponse;
import app.entity.Application;
import app.entity.Company;
import app.entity.RecruiterProfile;
import app.entity.User;
import app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final RecruiterRepository recruiterRepository;
    private final CompanyRepository companyRepository;
    private final NotificationService notifications;
    private final AuditService audit;

    public List<UserResponse> users() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    public UserResponse setEnabled(Long id, boolean enabled) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (u.getRole() == User.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin accounts cannot be disabled");
        }
        u.setEnabled(enabled);
        User saved = userRepository.save(u);
        audit.record(enabled ? "USER_ENABLED" : "USER_DISABLED", saved.getEmail(), null);
        return UserResponse.from(saved);
    }

    public List<RecruiterAdminResponse> recruiters() {
        return recruiterRepository.findAll().stream()
                .map(r -> new RecruiterAdminResponse(r.getId(), r.getUser().getName(), r.getUser().getEmail(),
                        r.getEffectiveCompanyName(), r.getIndustry(), r.getWebsite(), r.getLocation(),
                        r.isVerified(), r.getUser().isEnabled()))
                .toList();
    }

    @Transactional
    public RecruiterAdminResponse setVerified(Long id, boolean verified) {
        RecruiterProfile r = recruiterRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recruiter not found"));
        if (verified && (r.getEffectiveCompanyName() == null || r.getEffectiveCompanyName().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This recruiter has not completed the company profile yet");
        }
        r.setVerified(verified);
        if (r.getCompany() != null) {
            r.getCompany().setVerified(verified);
            companyRepository.save(r.getCompany());
        } else if (r.getCompanyName() != null && !r.getCompanyName().isBlank()) {
            companyRepository.findByNameIgnoreCase(r.getCompanyName().trim()).ifPresent(c -> {
                c.setVerified(verified);
                companyRepository.save(c);
                r.setCompany(c);
            });
        }
        RecruiterProfile savedRecruiter = recruiterRepository.save(r);
        audit.record(verified ? "RECRUITER_VERIFIED" : "RECRUITER_UNVERIFIED",
                savedRecruiter.getUser().getEmail(), savedRecruiter.getEffectiveCompanyName());
        notifications.send(savedRecruiter.getUser(),
                verified ? "Your company has been verified. You can now post jobs."
                         : "Your company verification was withdrawn. Your jobs are hidden until it is verified again.",
                "/recruiter");
        return new RecruiterAdminResponse(savedRecruiter.getId(), savedRecruiter.getUser().getName(), savedRecruiter.getUser().getEmail(),
                savedRecruiter.getEffectiveCompanyName(), savedRecruiter.getIndustry(), savedRecruiter.getWebsite(), savedRecruiter.getLocation(),
                savedRecruiter.isVerified(), savedRecruiter.getUser().isEnabled());
    }

    public Company verifyCompany(Long companyId, boolean verified) {
        Company c = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
        c.setVerified(verified);
        return companyRepository.save(c);
    }

    public List<app.dto.AuditLogResponse> auditLog() {
        return audit.recent();
    }

    public ReportResponse report() {
        return new ReportResponse(
                userRepository.count(),
                userRepository.countByRole(User.Role.JOB_SEEKER),
                userRepository.countByRole(User.Role.RECRUITER),
                jobRepository.count(),
                jobRepository.countByActiveTrue(),
                applicationRepository.count(),
                applicationRepository.countByStatus(Application.Status.APPLIED),
                applicationRepository.countByStatus(Application.Status.SHORTLISTED),
                applicationRepository.countByStatus(Application.Status.ACCEPTED),
                applicationRepository.countByStatus(Application.Status.REJECTED),
                recruiterRepository.countByVerifiedFalse());
    }
}
