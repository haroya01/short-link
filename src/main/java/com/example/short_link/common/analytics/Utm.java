package com.example.short_link.common.analytics;

public record Utm(String source, String medium, String campaign, String term, String content) {

  private static final Utm NONE = new Utm(null, null, null, null, null);

  public static Utm none() {
    return NONE;
  }
}
