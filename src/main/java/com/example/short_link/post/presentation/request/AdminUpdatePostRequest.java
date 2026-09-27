package com.example.short_link.post.presentation.request;

import jakarta.validation.constraints.Size;
import java.util.List;

public record AdminUpdatePostRequest(
    @Size(max = 200) String title, @Size(max = 100) List<@Size(max = 80) String> tags) {}
