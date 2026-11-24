package com.example.shortener.shortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateLinkRequest(
        @NotBlank @Size(max = 2048) String url,
        @Size(max = 32) String alias,
        Instant expiresAt) {}
