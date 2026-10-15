package com.example.short_link.federation.presentation.response;

import com.example.short_link.federation.application.RemoteAccountView;
import java.util.List;

public record RemoteAccountListResponse(List<RemoteAccountView> items) {}
