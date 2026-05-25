package com.example.short_link.admin.application.dto;

import java.util.List;

public record AdminLifecycle(int maxDay, List<DayPoint> days) {

  public record DayPoint(int day, long clicks, long contributingLinks) {}
}
