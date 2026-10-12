package app;

import app.entity.Application.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatusTransitionTest {
    private boolean ok(Status a, Status b) throws Exception {
        var m = Class.forName("app.service.ApplicationService")
                .getDeclaredMethod("allowedTransition", Status.class, Status.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, a, b);
    }

    @Test
    void rules() throws Exception {
        assertTrue(ok(Status.APPLIED, Status.SHORTLISTED));
        assertTrue(ok(Status.SHORTLISTED, Status.ACCEPTED));
        assertFalse(ok(Status.SHORTLISTED, Status.APPLIED));
        assertFalse(ok(Status.ACCEPTED, Status.REJECTED));
        assertFalse(ok(Status.REJECTED, Status.APPLIED));
    }
}
