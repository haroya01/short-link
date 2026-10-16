package com.example.short_link.federation.presentation.request;

import com.example.short_link.federation.domain.RelayMode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RelaySettingRequest(
    @NotNull RelayMode mode,
    @NotEmpty @Size(max = 10) List<@NotNull @Pattern(regexp = "[a-z]{2,3}") String> languages) {}
