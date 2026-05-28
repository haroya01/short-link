package com.example.short_link.link.stats.application.read;

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
import java.util.List;

public interface ClickDimensionReader {
  List<ReferrerClickRow> findTopReferrerClicks(Long linkId, int limit);

  List<ReferrerHostClickRow> findTopReferrerHostClicks(Long linkId, int limit);

  List<DeviceClickRow> findDeviceClicks(Long linkId);

  List<OsClickRow> findTopOsClicks(Long linkId, int limit);

  List<BrowserClickRow> findTopBrowserClicks(Long linkId, int limit);

  List<BotClickRow> findTopBotClicks(Long linkId, int limit);

  List<UtmCampaignClickRow> findTopUtmCampaignClicks(Long linkId, int limit);

  List<UtmSourceClickRow> findTopUtmSourceClicks(Long linkId, int limit);

  List<UtmMediumClickRow> findTopUtmMediumClicks(Long linkId, int limit);

  List<UtmContentClickRow> findTopUtmContentClicks(Long linkId, int limit);

  List<SourceChannelClickRow> findTopSourceChannelClicks(Long linkId, int limit);

  List<DestinationClickRow> findDestinationClicks(Long linkId);

  List<CountryClickRow> findTopCountryClicks(Long linkId, int limit);

  List<RegionClickRow> findTopRegionClicks(Long linkId, int limit);

  List<CityClickRow> findTopCityClicks(Long linkId, int limit);

  List<LanguageClickRow> findTopLanguageClicks(Long linkId, int limit);

  List<AsnClickRow> findTopAsnClicks(Long linkId, int limit);
}
