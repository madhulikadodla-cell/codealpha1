package com.codealpha.dataguard.dto;

import com.codealpha.dataguard.entity.DuplicateAttempt;
import java.time.Instant;

public record DuplicateAttemptResponse(Long id, String externalId, String submittedBy, Long matchedRecordId,
        String reason, Instant attemptedAt) {
    public static DuplicateAttemptResponse from(DuplicateAttempt attempt) {
        return new DuplicateAttemptResponse(attempt.getId(), attempt.getExternalId(), attempt.getSubmittedBy(),
                attempt.getMatchedRecordId(), attempt.getReason(), attempt.getAttemptedAt());
    }
}
