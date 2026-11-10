package com.example.short_link.link.application.dto;

import java.util.List;

public record AdminLinkClicksResult(List<AdminLinkClickView> items, String nextCursor) {}
