package com.example.short_link.common.time;

import java.util.Comparator;

public record DowHour(int dow, int hour) {

  public static final Comparator<DowHour> ORDER =
      Comparator.comparingInt(DowHour::dow).thenComparingInt(DowHour::hour);
}
