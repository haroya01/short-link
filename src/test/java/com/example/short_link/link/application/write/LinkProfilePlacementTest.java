package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.support.TestEntities;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LinkProfilePlacementTest {

  private final LinkRepository links = mock(LinkRepository.class);
  private final LinkProfilePlacement placement =
      new LinkProfilePlacement(new LinkOwnership(links), links);

  private static LinkEntity link(long id, String code) {
    return TestEntities.withId(new LinkEntity("https://" + code, code, 7L, null), id);
  }

  @Test
  void anOwnedLinksSlotPlacesAndFeaturesThatLink() {
    LinkEntity mine = link(1L, "mine001");
    when(links.findByShortCode(new ShortCode("mine001"))).thenReturn(Optional.of(mine));

    ProfileLinkSlot slot = placement.requireOwn(7L, new ShortCode("mine001"));
    slot.placeAt(3);
    slot.feature();

    assertThat(slot.id()).isEqualTo(mine.linkId());
    assertThat(mine.getProfileOrder()).isEqualTo(3);
    assertThat(mine.isProfileHighlighted()).isTrue();

    slot.placeAt(null);
    slot.unfeature();
    assertThat(mine.getProfileOrder()).isNull();
    assertThat(mine.isProfileHighlighted()).isFalse();
  }

  @Test
  void placedSlotsAreKeyedByShortCodeInProfileOrderAndCounted() {
    LinkEntity first = link(1L, "first01");
    LinkEntity second = link(2L, "second2");
    when(links.findAllByUserIdAndProfileOrderIsNotNullOrderByProfileOrderAsc(7L))
        .thenReturn(List.of(first, second));

    assertThat(placement.placedSlots(7L).keySet()).containsExactly("first01", "second2");
    assertThat(placement.placedCount(7L)).isEqualTo(2);

    placement.placedSlots(7L).get("second2").placeAt(1);
    assertThat(second.getProfileOrder()).isEqualTo(1);
  }

  @Test
  void featuringKeepsTheChosenLinkAndDropsTheOthers() {
    LinkEntity kept = link(1L, "kept001");
    LinkEntity other = link(2L, "other02");
    kept.featureOnProfile();
    other.featureOnProfile();
    when(links.findAllByUserIdAndProfileHighlightedIsTrue(7L)).thenReturn(List.of(kept, other));

    placement.unfeatureAllBut(7L, kept.linkId());

    assertThat(kept.isProfileHighlighted()).isTrue();
    assertThat(other.isProfileHighlighted()).isFalse();

    placement.unfeatureAll(7L);
    assertThat(kept.isProfileHighlighted()).isFalse();
  }
}
