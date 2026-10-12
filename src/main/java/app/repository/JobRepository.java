package app.repository;

import app.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findByRecruiterIdOrderByCreatedAtDesc(Long recruiterId);

    List<Job> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<Job> findByActiveTrueAndRecruiterVerifiedTrue();

    long countByActiveTrue();

    boolean existsByExternalId(String externalId);

    List<Job> findByExternalIdStartingWithAndActiveTrue(String prefix);

    /** Empty string / 0 mean "no filter". */
    @Query("select j from Job j where j.active = true and j.recruiter.verified = true "
            + "and (j.deadline is null or j.deadline >= current_date) "
            + "and (:keyword = '' or lower(j.title) like lower(concat('%', :keyword, '%')) "
            + "  or lower(j.description) like lower(concat('%', :keyword, '%')) "
            + "  or lower(j.requiredSkills) like lower(concat('%', :keyword, '%')) "
            + "  or lower(j.recruiter.companyName) like lower(concat('%', :keyword, '%')) "
            + "  or (j.company is not null and lower(j.company.name) like lower(concat('%', :keyword, '%')))) "
            + "and (:location = '' or lower(j.location) like lower(concat('%', :location, '%'))) "
            + "and (:jobType = '' or j.jobType = :jobType) "
            + "and (:minSalary = 0 or j.salaryMax >= :minSalary) "
            + "order by j.createdAt desc")
    List<Job> search(@Param("keyword") String keyword,
                     @Param("location") String location,
                     @Param("jobType") String jobType,
                     @Param("minSalary") int minSalary);

    @Query("select j from Job j where j.active = true and j.recruiter.verified = true "
            + "and (j.deadline is null or j.deadline >= current_date) "
            + "and (:keyword is null or :keyword = '' or lower(j.title) like lower(concat('%', :keyword, '%')) "
            + "  or lower(j.description) like lower(concat('%', :keyword, '%')) "
            + "  or lower(j.requiredSkills) like lower(concat('%', :keyword, '%')) "
            + "  or lower(j.recruiter.companyName) like lower(concat('%', :keyword, '%')) "
            + "  or (j.company is not null and lower(j.company.name) like lower(concat('%', :keyword, '%')))) "
            + "and (:location is null or :location = '' or lower(j.location) like lower(concat('%', :location, '%'))) "
            + "and (:jobType is null or :jobType = '' or j.jobType = :jobType) "
            + "and (:workMode is null or :workMode = '' or j.workMode = :workMode) "
            + "and (:experienceLevel is null or :experienceLevel = '' or j.experienceLevel = :experienceLevel) "
            + "and (:minSalary is null or :minSalary = 0 or j.salaryMax >= :minSalary) "
            + "order by j.createdAt desc")
    List<Job> advancedSearch(@Param("keyword") String keyword,
                             @Param("location") String location,
                             @Param("jobType") String jobType,
                             @Param("workMode") String workMode,
                             @Param("experienceLevel") String experienceLevel,
                             @Param("minSalary") Integer minSalary);
}
