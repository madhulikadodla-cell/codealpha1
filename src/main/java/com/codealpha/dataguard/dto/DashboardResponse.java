package com.codealpha.dataguard.dto;

import java.util.List;
import java.util.Map;

public record DashboardResponse(long totalRecords, long uniqueRecords, long duplicatesDetected,
        Map<String, Long> categoryCounts, List<DuplicateAttemptResponse> recentAttempts) {
}
