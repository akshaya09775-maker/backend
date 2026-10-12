package app.dto;

import jakarta.validation.constraints.Size;

public record ApplyRequest(@Size(max = 2000, message = "Cover letter must be at most 2000 characters")
                           String coverLetter) {}
