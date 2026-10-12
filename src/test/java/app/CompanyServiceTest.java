package app;

import app.dto.CompanyDetailResponse;
import app.dto.CompanyResponse;
import app.entity.Company;
import app.entity.CompanyFollow;
import app.entity.User;
import app.repository.CompanyFollowRepository;
import app.repository.CompanyRepository;
import app.repository.JobRepository;
import app.service.CompanyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock CompanyRepository companyRepository;
    @Mock CompanyFollowRepository followRepository;
    @Mock JobRepository jobRepository;

    @InjectMocks CompanyService companyService;

    User testUser;
    Company testCompany;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(101L).name("Test Seeker").email("seeker@test.com").build();
        testCompany = Company.builder()
                .id(1L)
                .name("Stripe")
                .slug("stripe")
                .industry("Fintech")
                .website("https://stripe.com")
                .verified(true)
                .build();
    }

    @Test
    void searchReturnsCompanyResponses() {
        when(companyRepository.search("stripe", null, null)).thenReturn(List.of(testCompany));
        when(followRepository.countByCompanyId(1L)).thenReturn(42L);
        when(followRepository.existsByUserIdAndCompanyId(101L, 1L)).thenReturn(true);
        when(jobRepository.findByCompanyIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        List<CompanyResponse> results = companyService.search("stripe", null, null, testUser);

        assertEquals(1, results.size());
        assertEquals("Stripe", results.get(0).name());
        assertEquals(42L, results.get(0).followerCount());
        assertTrue(results.get(0).following());
    }

    @Test
    void getDetailsReturnsCompanyWithJobsAndFollowStats() {
        when(companyRepository.findById(1L)).thenReturn(Optional.of(testCompany));
        when(followRepository.countByCompanyId(1L)).thenReturn(150L);
        when(followRepository.existsByUserIdAndCompanyId(101L, 1L)).thenReturn(false);
        when(jobRepository.findByCompanyIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        CompanyDetailResponse details = companyService.getDetails("1", testUser);

        assertNotNull(details);
        assertEquals("Stripe", details.name());
        assertEquals(150L, details.followerCount());
        assertFalse(details.following());
    }

    @Test
    void followAndUnfollowCompany() {
        when(companyRepository.findById(1L)).thenReturn(Optional.of(testCompany));
        when(followRepository.existsByUserIdAndCompanyId(101L, 1L)).thenReturn(false);

        boolean followed = companyService.follow(testUser, 1L);
        assertTrue(followed);
        verify(followRepository).save(any(CompanyFollow.class));

        boolean unfollowed = companyService.unfollow(testUser, 1L);
        assertFalse(unfollowed);
        verify(followRepository).deleteByUserIdAndCompanyId(101L, 1L);
    }
}
