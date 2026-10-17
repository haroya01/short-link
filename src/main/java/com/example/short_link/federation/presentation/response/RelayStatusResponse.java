package com.example.short_link.federation.presentation.response;

import com.example.short_link.federation.application.relay.RelaySubscriptions;
import com.example.short_link.federation.domain.RelayMode;
import java.util.List;
import java.util.Map;

public record RelayStatusResponse(
    boolean enabled,
    RelayMode mode,
    List<String> languages,
    boolean stopped,
    int queued,
    Map<String, Long> today,
    long kept,
    List<RelaySubscriptions.View> relays) {}
