package com.example.short_link.common.analytics;

import java.time.LocalDate;

public final class VisitProjections {

  private VisitProjections() {}

  public interface DailyVisitRow {
    LocalDate getDay();

    Long getCount();
  }

  public interface HourVisitRow {
    Integer getHour();

    Long getCount();
  }

  public interface HeatmapVisitRow {
    Integer getDow();

    Integer getHour();

    Long getCount();
  }

  public interface CountryVisitRow {
    String getCountry();

    Long getCount();
  }

  public interface DeviceVisitRow {
    String getDevice();

    Long getCount();
  }

  public interface BrowserVisitRow {
    String getBrowser();

    Long getCount();
  }

  public interface ReferrerHostVisitRow {
    String getHost();

    Long getCount();
  }

  public interface SourceChannelVisitRow {
    String getSource();

    Long getCount();
  }

  public interface UtmCampaignVisitRow {
    String getCampaign();

    Long getCount();
  }

  public interface UtmSourceVisitRow {
    String getSource();

    Long getCount();
  }
}
