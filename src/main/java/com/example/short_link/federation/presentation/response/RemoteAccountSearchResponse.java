package com.example.short_link.federation.presentation.response;

import com.example.short_link.federation.application.RemoteAccountView;
import java.util.List;

public record RemoteAccountSearchResponse(List<RemoteAccountView> items) {}
