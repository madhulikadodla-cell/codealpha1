package com.codealpha.dataguard;

import static org.assertj.core.api.Assertions.assertThat;

import com.codealpha.dataguard.dto.CreateRecordResponse;
import com.codealpha.dataguard.dto.RecordRequest;
import com.codealpha.dataguard.entity.AppUser;
import com.codealpha.dataguard.repository.AppUserRepository;
import com.codealpha.dataguard.repository.DataRecordRepository;
import com.codealpha.dataguard.repository.DuplicateAttemptRepository;
import com.codealpha.dataguard.service.RecordService;
import com.codealpha.dataguard.service.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RecordAndRegistrationTests {
    @Autowired private RecordService recordService;
    @Autowired private RegistrationService registrationService;
    @Autowired private DataRecordRepository records;
    @Autowired private DuplicateAttemptRepository attempts;
    @Autowired private AppUserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        attempts.deleteAll();
        records.deleteAll();
        users.deleteAll();
    }

    @Test
    void rejectsDuplicateExternalIdAndRecordsTheAttempt() {
        RecordRequest original = new RecordRequest("USER-101", "Jamie Chen", "jamie@example.com", "Customer", "Account opened");
        assertThat(recordService.create(original, "operator").duplicate()).isFalse();

        CreateRecordResponse duplicate = recordService.create(
                new RecordRequest("user-101", "Different Name", "other@example.com", "Customer", "Another account"), "operator");

        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.reason()).isEqualTo("EXTERNAL_ID");
        assertThat(records.count()).isEqualTo(1);
        assertThat(attempts.count()).isEqualTo(1);
    }

    @Test
    void rejectsNormalizedContentMatchEvenWhenExternalIdDiffers() {
        recordService.create(new RecordRequest("USER-201", "Jamie Chen", "JAMIE@example.com", "Customer", "Account opened"), "operator");

        CreateRecordResponse duplicate = recordService.create(
                new RecordRequest("USER-202", "  jamie chen ", "jamie@example.com", "customer", "Account   opened"), "operator");

        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.reason()).isEqualTo("CONTENT_MATCH");
        assertThat(records.count()).isEqualTo(1);
        assertThat(attempts.count()).isEqualTo(1);
    }

    @Test
    void registrationStoresOnlyEncodedPasswordAndStandardRole() {
        AppUser user = registrationService.register("new.user", "a-long-test-password");

        assertThat(user.getPasswordHash()).isNotEqualTo("a-long-test-password");
        assertThat(passwordEncoder.matches("a-long-test-password", user.getPasswordHash())).isTrue();
        assertThat(user.getRole()).isEqualTo("USER");
    }
}
