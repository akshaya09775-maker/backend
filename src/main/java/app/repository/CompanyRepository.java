package app.repository;

import app.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findBySlug(String slug);

    Optional<Company> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    @Query("SELECT c FROM Company c WHERE " +
           "(:q IS NULL OR :q = '' OR LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(c.tagline) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(c.specialties) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
           "(:industry IS NULL OR :industry = '' OR LOWER(c.industry) = LOWER(:industry)) AND " +
           "(:verified IS NULL OR c.verified = :verified)")
    List<Company> search(@Param("q") String q, @Param("industry") String industry, @Param("verified") Boolean verified);
}
