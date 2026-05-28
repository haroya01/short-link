package com.example.short_link.cta.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.cta.application.write.CtaOwnership;
import com.example.short_link.cta.domain.CtaEntity;
import com.example.short_link.cta.domain.CtaPurpose;
import com.example.short_link.cta.domain.CtaStyle;
import com.example.short_link.cta.domain.repository.CtaRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CtaQueryServiceTest {

  @Mock private CtaRepository ctaRepository;
  @Mock private CtaOwnership ctaOwnership;

  private CtaQueryService service;

  @BeforeEach
  void setUp() {
    service = new CtaQueryService(ctaRepository, ctaOwnership);
  }

  @Test
  void listMyCtasMapsAll() {
    CtaEntity c1 = new CtaEntity(7L, "L1", "https://1", CtaStyle.PRIMARY, CtaPurpose.BOOKING);
    CtaEntity c2 = new CtaEntity(7L, "L2", "https://2", CtaStyle.SECONDARY, CtaPurpose.SUBSCRIBE);
    when(ctaRepository.findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(7L))
        .thenReturn(List.of(c1, c2));

    List<CtaView> views = service.listMyCtas(7L);

    assertThat(views).hasSize(2);
    assertThat(views.get(0).label()).isEqualTo("L1");
    assertThat(views.get(0).purpose()).isEqualTo("BOOKING");
  }

  @Test
  void findOwnCtaReturnsViewIncludingDeletedFlag() {
    CtaEntity cta = new CtaEntity(7L, "L", "https://x", CtaStyle.PRIMARY, CtaPurpose.CUSTOM);
    cta.softDelete();
    when(ctaOwnership.requireOwned(7L, 42L)).thenReturn(cta);

    CtaView view = service.findOwnCta(7L, 42L);

    assertThat(view.deleted()).isTrue();
  }
}
