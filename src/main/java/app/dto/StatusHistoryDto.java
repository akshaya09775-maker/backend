package app.dto;

import app.entity.Application;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record StatusHistoryDto(Long id, Application.Status status, String note,
                               String updatedBy, LocalDateTime changedAt) {}
