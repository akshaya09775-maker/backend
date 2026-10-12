package app.repository;

import app.entity.CompanyFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface CompanyFollowRepository extends JpaRepository<CompanyFollow, Long> {

    boolean existsByUserIdAndCompanyId(Long userId, Long companyId);

    Optional<CompanyFollow> findByUserIdAndCompanyId(Long userId, Long companyId);

    long countByCompanyId(Long companyId);

    List<CompanyFollow> findByUserIdOrderByFollowedAtDesc(Long userId);

    @Transactional
    void deleteByUserIdAndCompanyId(Long userId, Long companyId);
}
