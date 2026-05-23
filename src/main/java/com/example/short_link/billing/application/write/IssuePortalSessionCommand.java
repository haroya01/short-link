package com.example.short_link.billing.application.write;

public record IssuePortalSessionCommand(Long userId) {

  public IssuePortalSessionCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
  }
}
