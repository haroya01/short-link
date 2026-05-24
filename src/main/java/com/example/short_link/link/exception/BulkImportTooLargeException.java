package com.example.short_link.link.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public final class BulkImportTooLargeException extends LinkException {

  private final int rows;
  private final int limit;

  public BulkImportTooLargeException(int rows, int limit) {
    super("bulk import too large: " + rows + " rows (limit " + limit + ")");
    this.rows = rows;
    this.limit = limit;
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.PAYLOAD_TOO_LARGE;
  }

  @Override
  public String code() {
    return "BULK_IMPORT_TOO_LARGE";
  }
}
