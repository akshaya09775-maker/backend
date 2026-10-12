package app.repository;

import app.entity.SavedJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface SavedJobRepository extends JpaRepository<SavedJob, Long> {

    boolean existsByUserIdAndJobId(Long userId, Long jobId);

    Optional<SavedJob> findByUserIdAndJobId(Long userId, Long jobId);

    List<SavedJob> findByUserIdOrderBySavedAtDesc(Long userId);

    long countByJobId(Long jobId);

    @Transactional
    void deleteByUserIdAndJobId(Long userId, Long jobId);
}
