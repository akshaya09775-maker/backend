package app.config;

import app.entity.*;
import app.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the first administrator on an empty database and repairs legacy recruiter data. No sample data is created. */
@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RecruiterRepository recruiterRepository;
    private final CompanyRepository companyRepository;
    private final JobRepository jobRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public DataInitializer(UserRepository userRepository,
                           RecruiterRepository recruiterRepository,
                           CompanyRepository companyRepository,
                           JobRepository jobRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${app.admin.email}") String adminEmail,
                           @Value("${app.admin.password}") String adminPassword) {
        this.userRepository = userRepository;
        this.recruiterRepository = recruiterRepository;
        this.companyRepository = companyRepository;
        this.jobRepository = jobRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        ensureAdmin();
        migrateExistingRecruiterCompanies();
    }

    private void ensureAdmin() {
        if (!userRepository.existsByRole(User.Role.ADMIN)) {
            userRepository.save(User.builder()
                    .name("Administrator")
                    .email(adminEmail.toLowerCase())
                    .password(passwordEncoder.encode(adminPassword))
                    .role(User.Role.ADMIN)
                    .build());
            log.info("Initial administrator account created: {}", adminEmail);
        }
    }

    private void migrateExistingRecruiterCompanies() {
        for (RecruiterProfile r : recruiterRepository.findAll()) {
            if (r.getCompany() == null && r.getCompanyName() != null && !r.getCompanyName().isBlank()) {
                String cName = r.getCompanyName().trim();
                Company comp = companyRepository.findByNameIgnoreCase(cName)
                        .orElseGet(() -> {
                            String slug = cName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
                            Company c = Company.builder()
                                    .name(cName)
                                    .slug(slug)
                                    .industry(r.getIndustry())
                                    .website(r.getWebsite())
                                    .headquarters(r.getLocation())
                                    .about(r.getDescription())
                                    .verified(r.isVerified())
                                    .build();
                            return companyRepository.save(c);
                        });
                r.setCompany(comp);
                recruiterRepository.save(r);

                // Link recruiter's jobs
                for (Job j : jobRepository.findByRecruiterIdOrderByCreatedAtDesc(r.getId())) {
                    if (j.getCompany() == null) {
                        j.setCompany(comp);
                        jobRepository.save(j);
                    }
                }
            }
        }
    }
}
