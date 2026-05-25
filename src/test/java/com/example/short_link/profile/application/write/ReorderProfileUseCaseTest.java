package com.example.short_link.profile.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.profile.application.ProfileCacheEviction;
import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.ProfileBlockType;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
import com.example.short_link.support.TestEntities;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReorderProfileUseCaseTest {

  @Mock private LinkRepository linkRepository;
  @Mock private ProfileBlockRepository profileBlockRepository;

  private ReorderProfileUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new ReorderProfileUseCase(
            linkRepository, profileBlockRepository, mock(ProfileCacheEviction.class));
  }

  @Test
  void assignsOrdersInGivenSequenceSkippingUnknown() {
    LinkEntity l1 = new LinkEntity("https://a", "a1", 7L, null);
    LinkEntity l2 = new LinkEntity("https://b", "b2", 7L, null);
    ProfileBlockEntity b1 = new ProfileBlockEntity(7L, ProfileBlockType.TEXT, "t", 99);
    TestEntities.withId(b1, 11L);
    when(linkRepository.findAllByUserIdAndProfileOrderIsNotNullOrderByProfileOrderAsc(7L))
        .thenReturn(List.of(l1, l2));
    when(profileBlockRepository.findAllByUserIdOrderByProfileOrderAsc(7L)).thenReturn(List.of(b1));

    java.util.List<ReorderItem> items = new java.util.ArrayList<>();
    items.add(new ReorderItem("LINK", "a1"));
    items.add(new ReorderItem("BLOCK", "11"));
    items.add(new ReorderItem("LINK", "b2"));
    items.add(new ReorderItem("UNKNOWN", "x"));
    items.add(null);
    items.add(new ReorderItem(null, "x"));
    items.add(new ReorderItem("LINK", null));
    items.add(new ReorderItem("LINK", "not-owned"));
    items.add(new ReorderItem("BLOCK", "not-a-number"));
    useCase.execute(new ReorderProfileCommand(7L, items));

    assertThat(l1.getProfileOrder()).isEqualTo(1);
    assertThat(b1.getProfileOrder()).isEqualTo(2);
    assertThat(l2.getProfileOrder()).isEqualTo(3);
  }

  private static void writeField(Object target, String name, Object value) {
    try {
      Field f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }
}
