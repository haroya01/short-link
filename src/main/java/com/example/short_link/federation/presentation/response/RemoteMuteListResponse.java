package com.example.short_link.federation.presentation.response;

import com.example.short_link.federation.application.RemoteMuteView;
import java.util.List;

public record RemoteMuteListResponse(List<RemoteMuteView> items) {}
