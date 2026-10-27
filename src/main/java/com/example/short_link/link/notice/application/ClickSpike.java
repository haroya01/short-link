package com.example.short_link.link.notice.application;

import java.util.OptionalLong;

record ClickSpike(int minClicks, double minRatio) {

  static final long MIN_LAST_HOUR = 3;

  boolean considers(long humanClicks) {
    return humanClicks >= minClicks;
  }

  boolean busy(long lastHour) {
    return lastHour >= MIN_LAST_HOUR;
  }

  OptionalLong timesUsual(long lastHour, long prior24Hours) {
    double ratio = lastHour / Math.max(prior24Hours / 24.0, 0.5);
    return ratio < minRatio ? OptionalLong.empty() : OptionalLong.of(Math.round(ratio));
  }
}
