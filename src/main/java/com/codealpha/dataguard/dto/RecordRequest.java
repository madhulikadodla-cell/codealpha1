package com.codealpha.dataguard.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecordRequest(
        @NotBlank @Size(max = 80) String externalId,
        @NotBlank @Size(max = 120) String name,
        @Email @Size(max = 160) String email,
        @NotBlank @Size(max = 60) String category,
        @NotBlank @Size(max = 500) String details) {
}
