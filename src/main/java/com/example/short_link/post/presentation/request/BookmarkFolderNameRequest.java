package com.example.short_link.post.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create / rename a bookmark folder. */
public record BookmarkFolderNameRequest(@NotBlank @Size(max = 60) String name) {}
