package com.example.short_link.link.application.read;

import com.example.short_link.common.net.BrowserHost;
import com.example.short_link.link.application.dto.LinkEvidence;
import com.example.short_link.link.application.dto.LinkEvidence.Destination;
import com.example.short_link.link.application.dto.LinkEvidence.DestinationKind;
import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkEvidenceRepository;
import com.example.short_link.link.domain.repository.LinkEvidenceRepository.LinkFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LinkEvidenceReader {

  private final LinkEvidenceRepository links;
  private final LinkDestinationRepository branches;

  @Transactional(readOnly = true)
  public Optional<LinkEvidence> find(ShortCode shortCode) {
    return links.findByShortCode(shortCode).map(this::evidence);
  }

  @Transactional(readOnly = true)
  public Optional<LinkEvidence.Image> image(ShortCode shortCode) {
    return links
        .findByShortCode(shortCode)
        .map(facts -> new LinkEvidence.Image(facts.getLinkId(), facts.getOgImage()));
  }

  private LinkEvidence evidence(LinkFacts facts) {
    return new LinkEvidence(
        facts.getLinkId(),
        facts.getOwnerId(),
        new LinkEvidence.Introduction(
            facts.getOgTitle(),
            facts.getOgDescription(),
            facts.getOgFetchStatus(),
            facts.getOgFetchedAt(),
            facts.getOgImage()),
        destinations(facts),
        facts.getOwnerId() == null
            ? null
            : new LinkEvidence.OwnerLinks(facts.getOwnerLinks(), facts.getOwnerDisabledLinks()));
  }

  private List<Destination> destinations(LinkFacts facts) {
    List<Destination> destinations = new ArrayList<>();
    destinations.add(
        new Destination(
            BrowserHost.of(facts.getOriginalUrl()),
            DestinationKind.ORIGINAL,
            null,
            null,
            null,
            null));
    for (LinkDestinationEntity branch : branches.findAllByLinkIdOrderByIdAsc(facts.getLinkId())) {
      if (branch.isEnabled()) {
        destinations.add(
            new Destination(
                BrowserHost.of(branch.getUrl()),
                DestinationKind.BRANCH,
                branch.getWeight(),
                branch.getCountryCode(),
                branch.getDeviceClass(),
                branch.getOs()));
      }
    }
    if (facts.getExpiredRedirectUrl() != null) {
      destinations.add(
          new Destination(
              BrowserHost.of(facts.getExpiredRedirectUrl()),
              DestinationKind.AFTER_EXPIRY,
              null,
              null,
              null,
              null));
    }
    return destinations;
  }
}
