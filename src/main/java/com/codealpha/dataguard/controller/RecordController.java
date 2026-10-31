package com.codealpha.dataguard.controller;

import com.codealpha.dataguard.dto.CreateRecordResponse;
import com.codealpha.dataguard.dto.DuplicateAttemptResponse;
import com.codealpha.dataguard.dto.RecordRequest;
import com.codealpha.dataguard.dto.RecordResponse;
import com.codealpha.dataguard.repository.DuplicateAttemptRepository;
import com.codealpha.dataguard.service.RecordService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RecordController {
    private final RecordService recordService;
    private final DuplicateAttemptRepository attempts;

    public RecordController(RecordService recordService, DuplicateAttemptRepository attempts) {
        this.recordService = recordService;
        this.attempts = attempts;
    }

    @GetMapping("/records")
    public List<RecordResponse> records(@RequestParam(required = false) String q,
            @RequestParam(required = false) String category) {
        return recordService.search(q, category);
    }

    @PostMapping("/records")
    public CreateRecordResponse create(@Valid @RequestBody RecordRequest request, Authentication authentication) {
        return recordService.create(request, authentication.getName());
    }

    @PutMapping("/records/{id}")
    public RecordResponse update(@PathVariable Long id, @Valid @RequestBody RecordRequest request) {
        return recordService.update(id, request);
    }

    @DeleteMapping("/records/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        recordService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reports/duplicates.csv")
    public ResponseEntity<byte[]> duplicateReport() {
        StringBuilder csv = new StringBuilder("attempt_id,external_id,submitted_by,matched_record_id,reason,attempted_at\n");
        List<DuplicateAttemptResponse> report = attempts.findTop250ByOrderByAttemptedAtDesc().stream()
                .map(DuplicateAttemptResponse::from).toList();
        for (DuplicateAttemptResponse attempt : report) {
            csv.append(attempt.id()).append(',').append(escape(attempt.externalId())).append(',')
                    .append(escape(attempt.submittedBy())).append(',')
                    .append(attempt.matchedRecordId() == null ? "" : attempt.matchedRecordId()).append(',')
                    .append(escape(attempt.reason())).append(',').append(attempt.attemptedAt()).append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=duplicate-report.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String escape(String value) {
        String safe = value == null ? "" : value.replace("\"", "\"\"");
        if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return "\"" + safe + "\"";
    }
}
