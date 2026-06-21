package com.example.short_link.link.stats.domain.repository.projection;

import java.time.LocalDate;

/**
 * Projection interfaces shared by the click-event read repositories. Sub-repositories (totals,
 * time, dimension, lifecycle, range, alert) all return these so application services don't have to
 * know which repository produced a row.
 */
public final class ClickProjections {

  private ClickProjections() {}

  public interface ReturnRateRow {
    Long getNewCount();

    Long getReturningCount();
  }

  public interface DayClickRow {
    Integer getDay();

    Long getCount();
  }

  public interface LinkClickCount {
    Long getLinkId();

    Long getCount();
  }

  public interface DailyClickRow {
    LocalDate getDay();

    Long getCount();
  }

  public interface DailyClicksByLinkRow {
    Long getLinkId();

    LocalDate getDay();

    Long getCount();
  }

  public interface HourClickRow {
    Integer getHour();

    Long getCount();
  }

  public interface DayOfWeekClickRow {
    Integer getDow();

    Long getCount();
  }

  public interface HeatmapRow {
    Integer getDow();

    Integer getHour();

    Long getCount();
  }

  public interface ReferrerClickRow {
    String getReferrer();

    Long getCount();
  }

  public interface ReferrerHostClickRow {
    String getHost();

    Long getCount();
  }

  /** referrer host 별 *최초* 클릭 시각(epoch 초) — 채널 점프("원래 채널 탈출") 판별용. */
  public interface HostFirstSeenRow {
    String getHost();

    Long getFirstSeenEpoch();
  }

  public interface DeviceClickRow {
    String getDevice();

    Long getCount();
  }

  public interface OsClickRow {
    String getOs();

    Long getCount();
  }

  public interface BrowserClickRow {
    String getBrowser();

    Long getCount();
  }

  public interface BotClickRow {
    String getBot();

    Long getCount();
  }

  public interface UtmCampaignClickRow {
    String getCampaign();

    Long getCount();
  }

  public interface UtmSourceClickRow {
    String getSource();

    Long getCount();
  }

  public interface UtmMediumClickRow {
    String getMedium();

    Long getCount();
  }

  public interface UtmContentClickRow {
    String getContent();

    Long getCount();
  }

  public interface SourceChannelClickRow {
    String getSource();

    Long getCount();
  }

  public interface DestinationClickRow {
    Long getDestinationId();

    Long getCount();
  }

  public interface CountryClickRow {
    String getCountry();

    Long getCount();
  }

  public interface RegionClickRow {
    String getRegion();

    Long getCount();
  }

  public interface CityClickRow {
    String getCity();

    Long getCount();
  }

  public interface LanguageClickRow {
    String getLanguage();

    Long getCount();
  }

  public interface AsnClickRow {
    Integer getAsn();

    String getOrganization();

    Long getCount();
  }
}
