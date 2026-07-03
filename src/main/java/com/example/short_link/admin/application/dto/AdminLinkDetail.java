package com.example.short_link.admin.application.dto;

import com.example.short_link.link.application.dto.LinkStats;

/**
 * A single link seen from the admin console: the browse-row metadata (owner, lifecycle, protection)
 * plus the same full click report the owner sees. The stats are produced by the owner-facing
 * assembler with the ownership check skipped — {@code /api/v1/admin/**} is already ADMIN-only.
 */
public record AdminLinkDetail(AdminLinkRow meta, LinkStats stats) {}
