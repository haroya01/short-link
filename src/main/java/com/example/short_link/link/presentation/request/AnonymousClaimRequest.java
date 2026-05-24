package com.example.short_link.link.presentation.request;

import com.example.short_link.link.application.AnonymousClaimService;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AnonymousClaimRequest(
    @NotEmpty @Size(max = AnonymousClaimService.MAX_TOKENS_PER_REQUEST) List<String> claimTokens) {}
