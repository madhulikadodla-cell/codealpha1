package com.codealpha.dataguard.dto;

import com.codealpha.dataguard.entity.DataRecord;
import java.time.Instant;

public record RecordResponse(Long id, String externalId, String name, String email, String category,
        String details, String createdBy, Instant createdAt, Instant updatedAt) {
    public static RecordResponse from(DataRecord record) {
        return new RecordResponse(record.getId(), record.getExternalId(), record.getName(), record.getEmail(),
                record.getCategory(), record.getDetails(), record.getCreatedBy(), record.getCreatedAt(), record.getUpdatedAt());
    }
}
