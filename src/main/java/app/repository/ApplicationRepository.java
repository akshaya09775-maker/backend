package app.repository;

import app.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findBySeekerIdOrderByAppliedAtDesc(Long seekerId);

    List<Application> findByJobId(Long jobId);

    boolean existsByJobIdAndSeekerId(Long jobId, Long seekerId);

    long countByJobId(Long jobId);

    long countByStatus(Application.Status status);

    List<Application> findAllByOrderByAppliedAtDesc();

    @Query("select a.job.id from Application a where a.seeker.id = :seekerId")
    List<Long> findAppliedJobIds(@Param("seekerId") Long seekerId);
}
