package app.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record SavedJobResponse(Long id, JobResponse job, LocalDateTime savedAt) {}
