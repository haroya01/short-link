package com.example.short_link.profile.infrastructure;

import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.BrowserClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.CountryClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.ReferrerHostClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.SourceChannelClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.UtmCampaignClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.UtmSourceClickRow;
import com.example.short_link.profile.application.visit.ProfileVisitBreakdownReader;
import com.example.short_link.profile.infrastructure.persistence.JpaProfileVisitRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaProfileVisitBreakdownReader implements ProfileVisitBreakdownReader {

  private final JpaProfileVisitRepository repository;

  @Override
  public List<CountryClickRow> topCountries(Long userId, int limit) {
    return repository.findCountryVisits(userId, PageRequest.ofSize(limit));
  }

  @Override
  public List<BrowserClickRow> topBrowsers(Long userId, int limit) {
    return repository.findBrowserVisits(userId, PageRequest.ofSize(limit));
  }

  @Override
  public List<ReferrerHostClickRow> topReferrerHosts(Long userId, int limit) {
    return repository.findReferrerHostVisits(userId, PageRequest.ofSize(limit));
  }

  @Override
  public List<SourceChannelClickRow> topSourceChannels(Long userId, int limit) {
    return repository.findSourceChannelVisits(userId, PageRequest.ofSize(limit));
  }

  @Override
  public List<UtmCampaignClickRow> topUtmCampaigns(Long userId, int limit) {
    return repository.findUtmCampaignVisits(userId, PageRequest.ofSize(limit));
  }

  @Override
  public List<UtmSourceClickRow> topUtmSources(Long userId, int limit) {
    return repository.findUtmSourceVisits(userId, PageRequest.ofSize(limit));
  }
}
