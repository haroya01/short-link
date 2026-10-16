package com.example.short_link.federation.presentation.response;

import com.example.short_link.federation.application.directory.DirectorySources;
import java.util.List;

public record DirectoryStatusResponse(
    boolean enabled, boolean running, List<DirectorySources.View> sources) {}
