package com.example.short_link.admin.application;

import java.util.List;

public record AdminCohort(int weeks, List<Row> rows) {

  public record Row(String cohortWeek, long size, List<Cell> cells) {}

  public record Cell(int weekOffset, long active, double ratio) {}
}
