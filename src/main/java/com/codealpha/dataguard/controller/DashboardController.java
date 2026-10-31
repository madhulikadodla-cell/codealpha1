package com.codealpha.dataguard.controller;

import com.codealpha.dataguard.dto.DashboardResponse;
import com.codealpha.dataguard.dto.DuplicateAttemptResponse;
import com.codealpha.dataguard.repository.DataRecordRepository;
import com.codealpha.dataguard.repository.DuplicateAttemptRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DashboardController {
    private final DataRecordRepository records;
    private final DuplicateAttemptRepository attempts;

    public DashboardController(DataRecordRepository records, DuplicateAttemptRepository attempts) {
        this.records = records;
        this.attempts = attempts;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        Map<String, Long> categories = new LinkedHashMap<>();
        for (Object[] row : records.countByCategory()) {
            categories.put((String) row[0], (Long) row[1]);
        }
        List<DuplicateAttemptResponse> recent = attempts.findTop8ByOrderByAttemptedAtDesc().stream()
                .map(DuplicateAttemptResponse::from).toList();
        long total = records.count();
        return new DashboardResponse(total, total, attempts.count(), categories, recent);
    }

    @GetMapping("/duplicate-attempts")
    public List<DuplicateAttemptResponse> duplicateAttempts() {
        return attempts.findTop250ByOrderByAttemptedAtDesc().stream().map(DuplicateAttemptResponse::from).toList();
    }
}
