package com.example.short_link.link.presentation.response;

import java.util.List;

public record AdminLinkClicksPage(List<AdminLinkClickResponse> items, String nextCursor) {}
