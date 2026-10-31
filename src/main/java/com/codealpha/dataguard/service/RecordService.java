package com.codealpha.dataguard.service;

import com.codealpha.dataguard.dto.CreateRecordResponse;
import com.codealpha.dataguard.dto.RecordRequest;
import com.codealpha.dataguard.dto.RecordResponse;
import com.codealpha.dataguard.entity.DataRecord;
import com.codealpha.dataguard.entity.DuplicateAttempt;
import com.codealpha.dataguard.repository.DataRecordRepository;
import com.codealpha.dataguard.repository.DuplicateAttemptRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordService {
    private final DataRecordRepository records;
    private final DuplicateAttemptRepository attempts;

    public RecordService(DataRecordRepository records, DuplicateAttemptRepository attempts) {
        this.records = records;
        this.attempts = attempts;
    }

    @Transactional
    public CreateRecordResponse create(RecordRequest request, String submittedBy) {
        String fingerprint = fingerprint(request);
        DataRecord match = records.findByExternalIdIgnoreCase(request.externalId().trim())
                .orElseGet(() -> records.findByFingerprint(fingerprint).orElse(null));
        if (match != null) {
            String reason = match.getExternalId().equalsIgnoreCase(request.externalId().trim())
                    ? "EXTERNAL_ID" : "CONTENT_MATCH";
            logAttempt(request.externalId(), submittedBy, match.getId(), reason);
            return CreateRecordResponse.duplicate(reason, match.getId());
        }

        DataRecord record = new DataRecord();
        populate(record, request);
        record.setFingerprint(fingerprint);
        record.setCreatedBy(submittedBy);
        record.setUpdatedAt(Instant.now());
        return CreateRecordResponse.created(RecordResponse.from(records.save(record)));
    }

    @Transactional
    public RecordResponse update(Long id, RecordRequest request) {
        DataRecord record = records.findById(id).orElseThrow(() -> new RecordNotFoundException(id));
        String normalizedId = request.externalId().trim();
        records.findByExternalIdIgnoreCase(normalizedId)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> { throw new DuplicateRecordException("EXTERNAL_ID", existing.getId()); });
        String fingerprint = fingerprint(request);
        records.findByFingerprint(fingerprint)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> { throw new DuplicateRecordException("CONTENT_MATCH", existing.getId()); });
        populate(record, request);
        record.setFingerprint(fingerprint);
        record.setUpdatedAt(Instant.now());
        return RecordResponse.from(records.save(record));
    }

    @Transactional(readOnly = true)
    public List<RecordResponse> search(String query, String category) {
        return records.search(blankToNull(query), blankToNull(category)).stream().map(RecordResponse::from).toList();
    }

    @Transactional
    public void delete(Long id) {
        if (!records.existsById(id)) {
            throw new RecordNotFoundException(id);
        }
        records.deleteById(id);
    }

    private void populate(DataRecord record, RecordRequest request) {
        record.setExternalId(request.externalId().trim());
        record.setName(request.name().trim());
        record.setEmail(request.email() == null || request.email().isBlank() ? null : request.email().trim());
        record.setCategory(request.category().trim());
        record.setDetails(request.details().trim());
    }

    private void logAttempt(String externalId, String submittedBy, Long matchedId, String reason) {
        DuplicateAttempt attempt = new DuplicateAttempt();
        attempt.setExternalId(externalId.trim());
        attempt.setSubmittedBy(submittedBy);
        attempt.setMatchedRecordId(matchedId);
        attempt.setReason(reason);
        attempts.save(attempt);
    }

    private String fingerprint(RecordRequest request) {
        String canonical = String.join("\u001f",
                normalize(request.name()), normalize(request.email()), normalize(request.category()), normalize(request.details()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static class RecordNotFoundException extends RuntimeException {
        public RecordNotFoundException(Long id) { super("Record " + id + " was not found."); }
    }

    public static class DuplicateRecordException extends RuntimeException {
        private final String reason;
        private final Long matchedRecordId;
        public DuplicateRecordException(String reason, Long matchedRecordId) {
            super("An existing record matches this submission.");
            this.reason = reason;
            this.matchedRecordId = matchedRecordId;
        }
        public String getReason() { return reason; }
        public Long getMatchedRecordId() { return matchedRecordId; }
    }
}
