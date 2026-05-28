package com.example.short_link.profile.application.visit;

import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.BrowserClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.CountryClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.ReferrerHostClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.SourceChannelClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.UtmCampaignClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.UtmSourceClickRow;
import java.util.List;

public interface ProfileVisitBreakdownReader {
  List<CountryClickRow> topCountries(Long userId, int limit);

  List<BrowserClickRow> topBrowsers(Long userId, int limit);

  List<ReferrerHostClickRow> topReferrerHosts(Long userId, int limit);

  List<SourceChannelClickRow> topSourceChannels(Long userId, int limit);

  List<UtmCampaignClickRow> topUtmCampaigns(Long userId, int limit);

  List<UtmSourceClickRow> topUtmSources(Long userId, int limit);
}
