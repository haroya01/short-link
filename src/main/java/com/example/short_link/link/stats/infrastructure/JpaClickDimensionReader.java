package com.example.short_link.link.stats.infrastructure;

import com.example.short_link.link.stats.application.read.ClickDimensionReader;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.AsnClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.BotClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.BrowserClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.CityClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.CountryClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DestinationClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DeviceClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.LanguageClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.OsClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.ReferrerClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.ReferrerHostClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.RegionClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.SourceChannelClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.UtmCampaignClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.UtmContentClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.UtmMediumClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.UtmSourceClickRow;
import com.example.short_link.link.stats.infrastructure.persistence.JpaClickDimensionReadRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaClickDimensionReader implements ClickDimensionReader {

  private final JpaClickDimensionReadRepository repository;

  @Override
  public List<ReferrerClickRow> findTopReferrerClicks(Long linkId, int limit) {
    return repository.findReferrerClicks(linkId, PageRequest.ofSize(limit));
  }

  @Override
  public List<ReferrerHostClickRow> findTopReferrerHostClicks(Long linkId, int limit) {
    return repository.findReferrerHostClicks(linkId, PageRequest.ofSize(limit));
  }

  @Override
  public List<DeviceClickRow> findDeviceClicks(Long linkId) {
    return repository.findDeviceClicks(linkId);
  }

  @Override
  public List<OsClickRow> findTopOsClicks(Long linkId, int limit) {
    return repository.findOsClicks(linkId, PageRequest.ofSize(limit));
  }

  @Override
  public List<BrowserClickRow> findTopBrowserClicks(Long linkId, int limit) {
    return repository.findBrowserClicks(linkId, PageRequest.ofSize(limit));
  }

  @Override
  public List<BotClickRow> findTopBotClicks(Long linkId, int limit) {
    return repository.findBotClicks(linkId, PageRequest.ofSize(limit));
  }

  @Override
  public List<UtmCampaignClickRow> findTopUtmCampaignClicks(Long linkId, int limit) {
    return repository.findUtmCampaignClicks(linkId, PageRequest.ofSize(limit));
  }

  @Override
  public List<UtmSourceClickRow> findTopUtmSourceClicks(Long linkId, int limit) {
    return repository.findUtmSourceClicks(linkId, PageRequest.ofSize(limit));
  }

  @Override
  public List<UtmMediumClickRow> findTopUtmMediumClicks(Long linkId, int limit) {
    return repository.findUtmMediumClicks(linkId, PageRequest.ofSize(limit));
  }

  @Override
  public List<UtmContentClickRow> findTopUtmContentClicks(Long linkId, int limit) {
    return repository.findUtmContentClicks(linkId, PageRequest.ofSize(limit));
  }

  @Override
  public List<SourceChannelClickRow> findTopSourceChannelClicks(Long linkId, int limit) {
    return repository.findSourceChannelClicks(linkId, PageRequest.ofSize(limit));
  }

  @Override
  public List<DestinationClickRow> findDestinationClicks(Long linkId) {
    return repository.findDestinationClicks(linkId);
  }

  @Override
  public List<CountryClickRow> findTopCountryClicks(Long linkId, int limit) {
    return repository.findCountryClicks(linkId, PageRequest.ofSize(limit));
  }

  @Override
  public List<RegionClickRow> findTopRegionClicks(Long linkId, int limit) {
    return repository.findRegionClicks(linkId, PageRequest.ofSize(limit));
  }

  @Override
  public List<CityClickRow> findTopCityClicks(Long linkId, int limit) {
    return repository.findCityClicks(linkId, PageRequest.ofSize(limit));
  }

  @Override
  public List<LanguageClickRow> findTopLanguageClicks(Long linkId, int limit) {
    return repository.findLanguageClicks(linkId, PageRequest.ofSize(limit));
  }

  @Override
  public List<AsnClickRow> findTopAsnClicks(Long linkId, int limit) {
    return repository.findAsnClicks(linkId, PageRequest.ofSize(limit));
  }
}
