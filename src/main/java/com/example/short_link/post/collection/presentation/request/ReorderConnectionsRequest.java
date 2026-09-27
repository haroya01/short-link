package com.example.short_link.post.collection.presentation.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ReorderConnectionsRequest(@NotEmpty List<Long> connectionIds) {}
