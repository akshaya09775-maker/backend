package app.controller;

import app.dto.JobResponse;
import app.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.ResponseEntity;

import java.util.List;

/** Job browsing with rich multi-filter faceted search. */
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;
    private final app.service.JobImportService importService;

    @GetMapping
    public ResponseEntity<List<JobResponse>> search(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String location,
                                    @RequestParam(required = false) String jobType,
                                    @RequestParam(required = false) String workMode,
                                    @RequestParam(required = false) String experienceLevel,
                                    @RequestParam(required = false) Integer minSalary,
                                    @RequestParam(required = false) Long companyId,
                                    @RequestParam(required = false) String sort,
                                    @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer size) {
        List<JobResponse> all = jobService.searchAdvanced(keyword, location, jobType, workMode, experienceLevel,
                minSalary, companyId, sort);
        // Few results for a keyword? Fetch current openings for it live (any employer), then search again.
        if (companyId == null && keyword != null && !keyword.isBlank() && all.size() < 5
                && (page == null || page == 0) && importService.importForQuery(keyword, location) > 0) {
            all = jobService.searchAdvanced(keyword, location, jobType, workMode, experienceLevel,
                    minSalary, companyId, sort);
        }
        // Without "size" the full list is returned (backwards compatible); with it the result is paged.
        if (size == null) {
            return ResponseEntity.ok().header("X-Total-Count", String.valueOf(all.size())).body(all);
        }
        int pageSize = Math.max(1, Math.min(size, 50));
        int from = (int) Math.min((long) Math.max(page == null ? 0 : page, 0) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        return ResponseEntity.ok().header("X-Total-Count", String.valueOf(all.size())).body(all.subList(from, to));
    }

    @GetMapping("/{id}")
    public JobResponse get(@PathVariable Long id) {
        return jobService.get(id);
    }
}
