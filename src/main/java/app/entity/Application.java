package app.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "applications",
        uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "seeker_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(optional = false)
    @JoinColumn(name = "seeker_id", nullable = false)
    private JobSeekerProfile seeker;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.APPLIED;

    @Column(length = 2000)
    private String coverLetter;

    private LocalDateTime appliedAt;
    private LocalDateTime updatedAt;

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt ASC")
    private List<ApplicationStatusHistory> statusHistories = new ArrayList<>();

    @PrePersist
    void onCreate() {
        appliedAt = LocalDateTime.now();
        updatedAt = appliedAt;
        if (statusHistories.isEmpty()) {
            addStatusHistory(this.status, "Application submitted", "Candidate");
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addStatusHistory(Status newStatus, String note, String updatedBy) {
        this.status = newStatus;
        if (this.statusHistories == null) {
            this.statusHistories = new ArrayList<>();
        }
        this.statusHistories.add(ApplicationStatusHistory.builder()
                .application(this)
                .status(newStatus)
                .note(note)
                .updatedBy(updatedBy)
                .changedAt(LocalDateTime.now())
                .build());
    }

    public enum Status {
        APPLIED,
        UNDER_REVIEW,
        SHORTLISTED,
        INTERVIEW,
        OFFERED,
        ACCEPTED,
        REJECTED,
        WITHDRAWN
    }
}
