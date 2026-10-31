package com.codealpha.dataguard.dto;

public record CreateRecordResponse(boolean duplicate, String reason, Long matchedRecordId, RecordResponse record) {
    public static CreateRecordResponse created(RecordResponse record) {
        return new CreateRecordResponse(false, null, null, record);
    }

    public static CreateRecordResponse duplicate(String reason, Long matchedRecordId) {
        return new CreateRecordResponse(true, reason, matchedRecordId, null);
    }
}
