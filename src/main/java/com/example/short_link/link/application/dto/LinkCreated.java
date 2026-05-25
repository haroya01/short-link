package com.example.short_link.link.application.dto;

public record LinkCreated(String shortCode, String claimToken) {

  public LinkCreated(String shortCode) {
    this(shortCode, null);
  }
}
