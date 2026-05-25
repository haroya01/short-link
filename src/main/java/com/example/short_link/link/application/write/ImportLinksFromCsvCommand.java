package com.example.short_link.link.application.write;

import java.io.InputStream;

public record ImportLinksFromCsvCommand(Long userId, InputStream csv) {

  public ImportLinksFromCsvCommand {
    if (userId == null) {
      throw new IllegalArgumentException("userId required");
    }
    if (csv == null) {
      throw new IllegalArgumentException("csv required");
    }
  }
}
