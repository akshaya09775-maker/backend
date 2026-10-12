package app.service;

import app.dto.AuditLogResponse;
import app.entity.AuditLog;
import app.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    /** Records an action by the currently authenticated user (or "anonymous"). */
    public void record(String action, String target, String detail) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        String actor = (a == null || a.getName() == null || "anonymousUser".equals(a.getName()))
                ? "anonymous" : a.getName();
        recordAs(actor, action, target, detail);
    }

    public void recordAs(String actor, String action, String target, String detail) {
        String d = detail == null ? null : (detail.length() > 480 ? detail.substring(0, 480) : detail);
        repository.save(AuditLog.builder().actor(actor).action(action).target(target).detail(d).build());
    }

    public List<AuditLogResponse> recent() {
        return repository.findTop200ByOrderByIdDesc().stream().map(AuditLogResponse::from).toList();
    }
}
