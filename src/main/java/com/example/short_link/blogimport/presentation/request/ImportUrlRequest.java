package com.example.short_link.blogimport.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ImportUrlRequest(@NotBlank @Size(max = 512) String url) {}
