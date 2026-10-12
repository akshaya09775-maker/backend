package app.controller;

import app.dto.CompanyDetailResponse;
import app.dto.CompanyResponse;
import app.entity.User;
import app.security.CurrentUser;
import app.service.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;
    private final CurrentUser currentUser;
    private final app.service.JobImportService importService;

    @GetMapping
    public List<CompanyResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) Boolean verified) {
        User user = null;
        try {
            user = currentUser.get();
        } catch (Exception ignored) {}
        List<CompanyResponse> found = companyService.search(q, industry, verified, user);
        // Unknown employer? Look it up live so any company can be found, then search again.
        if (found.isEmpty() && q != null && !q.isBlank() && importService.importForQuery(q, null) > 0) {
            found = companyService.search(q, industry, verified, user);
        }
        return found;
    }

    @GetMapping("/{slugOrId}")
    public CompanyDetailResponse details(@PathVariable String slugOrId) {
        User user = null;
        try {
            user = currentUser.get();
        } catch (Exception ignored) {}
        return companyService.getDetails(slugOrId, user);
    }

    @PostMapping("/{id}/follow")
    public Map<String, Object> follow(@PathVariable Long id) {
        boolean following = companyService.follow(currentUser.get(), id);
        return Map.of("following", following, "companyId", id);
    }

    @DeleteMapping("/{id}/follow")
    public Map<String, Object> unfollow(@PathVariable Long id) {
        boolean following = companyService.unfollow(currentUser.get(), id);
        return Map.of("following", following, "companyId", id);
    }

    @GetMapping("/following")
    public List<CompanyResponse> followed() {
        return companyService.getFollowed(currentUser.get());
    }
}
