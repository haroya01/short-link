package com.example.short_link.cta.application.read;

import com.example.short_link.cta.application.write.CtaOwnership;
import com.example.short_link.cta.domain.CtaEntity;
import com.example.short_link.cta.domain.repository.CtaRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CtaQueryService {

  private final CtaRepository ctaRepository;
  private final CtaOwnership ctaOwnership;

  /** 활성 CTA 만 (soft-delete 제외). */
  public List<CtaView> listMyCtas(Long userId) {
    return ctaRepository.findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId).stream()
        .map(CtaView::from)
        .toList();
  }

  /** soft-delete 된 것도 조회 가능 — 과거 글의 CTA_REF 가 가리키는 CTA 가 삭제됐을 때 표시 위해. */
  public CtaView findOwnCta(Long userId, Long ctaId) {
    CtaEntity cta = ctaOwnership.requireOwned(userId, ctaId);
    return CtaView.from(cta);
  }
}
