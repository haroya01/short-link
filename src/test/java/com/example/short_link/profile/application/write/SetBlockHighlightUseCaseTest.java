package com.example.short_link.profile.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.profilebinding.domain.repository.LinkProfileBindingRepository;
import com.example.short_link.profile.application.ProfileCacheEviction;
import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.ProfileBlockType;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.support.TestEntities;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SetBlockHighlightUseCaseTest {

  @Mock private ProfileBlockRepository profileBlockRepository;
  @Mock private LinkRepository linkRepository;
  private SetBlockHighlightUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new SetBlockHighlightUseCase(
            profileBlockRepository,
            new ProfileFeaturedSlot(
                linkRepository, mock(LinkProfileBindingRepository.class), profileBlockRepository),
            mock(ProfileCacheEviction.class));
  }

  private ProfileBlockEntity block(long id, long owner, ProfileBlockType type) {
    ProfileBlockEntity block = new ProfileBlockEntity(owner, type, "{}", 0);
    TestEntities.withId(block, id);
    return block;
  }

  @Test
  void throwsWhenBlockMissing() {
    when(profileBlockRepository.findById(9L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(new SetBlockHighlightCommand(7L, 9L, true)))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void throwsWhenNotOwner() {
    when(profileBlockRepository.findById(9L))
        .thenReturn(Optional.of(block(9L, 99L, ProfileBlockType.EVENT)));

    assertThatThrownBy(() -> useCase.execute(new SetBlockHighlightCommand(7L, 9L, true)))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void onlyEventAndProductBlocksCanBeFeatured() {
    ProfileBlockEntity text = block(9L, 7L, ProfileBlockType.TEXT);
    when(profileBlockRepository.findById(9L)).thenReturn(Optional.of(text));

    assertThatThrownBy(() -> useCase.execute(new SetBlockHighlightCommand(7L, 9L, true)))
        .isInstanceOf(ProfileException.class)
        .hasMessageContaining("only event and product blocks can be featured");
    assertThat(text.isProfileHighlighted()).isFalse();
  }

  @Test
  void featuringABlockTakesTheSlotFromTheFeaturedLinkAndOtherBlocks() {
    ProfileBlockEntity event = block(9L, 7L, ProfileBlockType.EVENT);
    ProfileBlockEntity product = block(10L, 7L, ProfileBlockType.PRODUCT_CARD);
    product.feature();
    LinkEntity link = new LinkEntity("https://x", "abc", 7L, null);
    TestEntities.withId(link, 1L);
    link.featureOnProfile();
    when(profileBlockRepository.findById(9L)).thenReturn(Optional.of(event));
    when(profileBlockRepository.findAllByUserIdAndProfileHighlightedIsTrue(7L))
        .thenReturn(List.of(product));
    when(linkRepository.findAllByUserIdAndProfileHighlightedIsTrue(7L)).thenReturn(List.of(link));

    useCase.execute(new SetBlockHighlightCommand(7L, 9L, true));

    assertThat(event.isProfileHighlighted()).isTrue();
    assertThat(product.isProfileHighlighted()).isFalse();
    assertThat(link.isProfileHighlighted()).isFalse();
  }

  @Test
  void unfeaturingLeavesTheSlotEmpty() {
    ProfileBlockEntity event = block(9L, 7L, ProfileBlockType.EVENT);
    event.feature();
    when(profileBlockRepository.findById(9L)).thenReturn(Optional.of(event));

    useCase.execute(new SetBlockHighlightCommand(7L, 9L, false));

    assertThat(event.isProfileHighlighted()).isFalse();
  }
}
