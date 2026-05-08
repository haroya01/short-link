package com.example.short_link.link.application;

public class BulkImportTooLargeException extends RuntimeException {

  private final int rows;
  private final int limit;

  public BulkImportTooLargeException(int rows, int limit) {
    super("bulk import too large: " + rows + " rows (limit " + limit + ")");
    this.rows = rows;
    this.limit = limit;
  }

  public int getRows() {
    return rows;
  }

  public int getLimit() {
    return limit;
  }
}
