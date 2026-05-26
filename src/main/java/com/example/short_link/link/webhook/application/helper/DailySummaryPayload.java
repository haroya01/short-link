package com.example.short_link.link.webhook.application.helper;

import com.example.short_link.link.domain.ShortCode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generic kurl daily-summary payload shape. Keep ordering stable (LinkedHashMap) so receivers that
 * pretty-print the JSON show fields in the same order across days.
 */
public record DailySummaryPayload(
    ShortCode shortCode,
    String windowStartLocal,
    String windowEndLocal,
    long totalClicks,
    long humanClicks,
    long botClicks,
    long uniqueVisitors,
    String topChannel,
    String topCountry,
    String topDevice,
    int peakHour,
    long peakHourClicks,
    Double vsYesterday,
    Double vs7DayAvg) {

  public Map<String, Object> toJsonMap() {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("type", "daily_summary");
    root.put("shortCode", shortCode);
    root.put("windowStart", windowStartLocal);
    root.put("windowEnd", windowEndLocal);

    Map<String, Object> clicks = new LinkedHashMap<>();
    clicks.put("total", totalClicks);
    clicks.put("human", humanClicks);
    clicks.put("bot", botClicks);
    clicks.put("unique", uniqueVisitors);
    root.put("clicks", clicks);

    Map<String, Object> top = new LinkedHashMap<>();
    top.put("channel", topChannel);
    top.put("country", topCountry);
    top.put("device", topDevice);
    root.put("top", top);

    Map<String, Object> peak = new LinkedHashMap<>();
    peak.put("hour", peakHour);
    peak.put("clicks", peakHourClicks);
    root.put("peak", peak);

    Map<String, Object> delta = new LinkedHashMap<>();
    delta.put("vsYesterday", vsYesterday);
    delta.put("vs7DayAvg", vs7DayAvg);
    root.put("delta", delta);

    return root;
  }
}
