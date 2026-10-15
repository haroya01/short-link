package com.example.short_link.blogimport.domain;

public enum ImportSourceStatus {
  PENDING_VERIFICATION,
  IMPORTING,
  SYNCING,
  ERROR,
  DISCONNECTED;

  public boolean verified() {
    return this == IMPORTING || this == SYNCING || this == ERROR;
  }
}
