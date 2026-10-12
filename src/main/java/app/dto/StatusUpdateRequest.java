package app.dto;

import app.entity.Application;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(
        @NotNull(message = "Status is required") Application.Status status,
        String note) {

    public StatusUpdateRequest(Application.Status status) {
        this(status, null);
    }
}
