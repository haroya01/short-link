package com.example.short_link.cta.application.read;

import com.example.short_link.cta.domain.CtaEntity;
import com.example.short_link.cta.domain.repository.CtaRepository;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.domain.ShortCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CtaCards {

  private final CtaRepository ctas;
  private final ShortLinkUrlBuilder urls;

  public Map<Long, CtaCard> findOwnedBy(Long authorId, List<Long> ctaIds) {
    Map<Long, CtaCard> cards = new LinkedHashMap<>();
    for (Long ctaId : ctaIds) {
      if (cards.containsKey(ctaId)) continue;
      ctas.findById(ctaId)
          .filter(cta -> cta.isOwnedBy(authorId))
          .ifPresent(cta -> cards.put(ctaId, card(cta)));
    }
    return cards;
  }

  private CtaCard card(CtaEntity cta) {
    // 추적 링크가 있으면 이 글의 클릭으로 집계하고, 없으면 원본 URL로 이동한다.
    String url =
        cta.getTrackedShortCode() != null
            ? urls.build(ShortCode.of(cta.getTrackedShortCode()))
            : cta.getUrl();
    return new CtaCard(
        cta.getId(),
        cta.getLabel(),
        url,
        cta.getStyle().name(),
        cta.getPurpose().name(),
        cta.isDeleted());
  }
}
