package com.example.short_link.admin.application.dto;

import com.example.short_link.link.application.dto.LinkStats;

public record AdminLinkDetail(AdminLinkRow meta, LinkStats stats) {}
