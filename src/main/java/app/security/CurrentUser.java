package app.security;

import app.entity.User;
import app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Gives services access to the logged-in user of the current request. */
@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final UserRepository userRepository;

    /** The logged-in user, or null for anonymous visitors. */
    public User getOrNull() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a.getName() == null
                || "anonymousUser".equals(a.getName())) {
            return null;
        }
        return userRepository.findByEmail(a.getName()).orElse(null);
    }

    public User get() {
        User u = getOrNull();
        if (u == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        return u;
    }
}
