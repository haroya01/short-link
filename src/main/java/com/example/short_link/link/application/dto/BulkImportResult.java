package com.example.short_link.link.application.dto;

import java.util.List;

public record BulkImportResult(int ok, int failed, List<BulkImportRow> rows) {}
