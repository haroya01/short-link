package com.example.short_link.portability.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// The file's text: Mastodon export files are small (a line per account), so a JSON body is enough.
public record StartImportRequest(
    @NotBlank String kind, @NotBlank @Size(max = 1_000_000) String csv) {}
