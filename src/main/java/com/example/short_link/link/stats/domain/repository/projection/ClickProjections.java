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

  public interface UtmTermClickRow {
    String getTerm();

    Long getCount();
  }

  public interface SourceChannelClickRow {
    String getSource();

    Long getCount();
  }

  /** 인앱 브라우저(카카오톡·인스타그램 …) 별 사람 클릭 — 일반 브라우저는 client_app 이 NULL 이라 빠진다. */
  public interface ClientAppClickRow {
    String getApp();

    Long getCount();
  }

  /** Sec-Fetch-Site 값별 사람 클릭 — 헤더를 안 보내는 브라우저의 클릭은 NULL 이라 빠진다. */
  public interface FetchSiteClickRow {
    String getFetchSite();

    Long getCount();
  }

  /** 링크를 품고 있던 글별 사람 클릭 — 제목은 post 슬라이스 포트로 따로 붙인다. */
  public interface PostClickRow {
    Long getPostId();

    Long getCount();
  }

  /**
   * referrer host 하나를 시간축까지 포함해 읽은 행 — 클릭 수, 첫 등장 시각(epoch 초), 그 채널에서 온 방문자 수와 그중 재방문 수. 채널을 "막대
   * 하나"가 아니라 "언제 시작해 얼마나 붙잡았나"로 보게 한다.
   */
  public interface ChannelDepthRow {
    String getHost();

    Long getCount();

    Long getFirstSeenEpoch();

    Long getVisitors();

    Long getReturningVisitors();
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
