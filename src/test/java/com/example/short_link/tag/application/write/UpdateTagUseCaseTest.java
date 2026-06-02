package com.example.short_link.tag.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.tag.application.dto.TagSummary;
import com.example.short_link.tag.application.read.TagQueryService;
import com.example.short_link.tag.domain.TagEntity;
import com.example.short_link.tag.domain.repository.TagRepository;
import com.example.short_link.tag.exception.TagException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UpdateTagUseCaseTest {

  @Mock private TagRepository tagRepository;
  @Mock private TagQueryService tagQueryService;
  @InjectMocks private UpdateTagUseCase useCase;

  private static final long USER = 7L;
  private static final long ID = 1L;

  private TagEntity ownedTag(String name, String color) {
    TagEntity tag = mock(TagEntity.class);
    lenient().when(tag.getId()).thenReturn(ID);
    lenient().when(tag.getUserId()).thenReturn(USER);
    lenient().when(tag.getName()).thenReturn(name);
    lenient().when(tag.getColor()).thenReturn(color);
    lenient().when(tag.getCreatedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
    return tag;
  }

  @Test
  void throwsWhenTagNotFound() {
    when(tagRepository.findById(ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(USER, ID, "new", null))
        .isInstanceOf(TagException.class);
  }

  @Test
  void throwsWhenTagOwnedByAnotherUser() {
    TagEntity tag = ownedTag("old", null);
    when(tag.getUserId()).thenReturn(999L);
    when(tagRepository.findById(ID)).thenReturn(Optional.of(tag));

    assertThatThrownBy(() -> useCase.execute(USER, ID, "new", null))
        .isInstanceOf(TagException.class);
  }

  @Test
  void throwsWhenRenamingToExistingName() {
    TagEntity tag = ownedTag("old", null);
    when(tagRepository.findById(ID)).thenReturn(Optional.of(tag));
    when(tagRepository.findFirstByUserIdAndName(USER, "new"))
        .thenReturn(Optional.of(mock(TagEntity.class)));

    assertThatThrownBy(() -> useCase.execute(USER, ID, "new", null))
        .isInstanceOf(TagException.class);
  }

  @Test
  void renamesWhenNewNameIsFree() {
    TagEntity tag = ownedTag("old", null);
    when(tagRepository.findById(ID)).thenReturn(Optional.of(tag));
    when(tagRepository.findFirstByUserIdAndName(USER, "new")).thenReturn(Optional.empty());
    when(tagQueryService.countMap(List.of(ID))).thenReturn(Map.of(ID, 4L));

    TagSummary summary = useCase.execute(USER, ID, "new", null);

    verify(tag).rename("new");
    assertThat(summary.linkCount()).isEqualTo(4L);
  }

  @Test
  void skipsDuplicateCheckWhenNameUnchanged() {
    TagEntity tag = ownedTag("same", null);
    when(tagRepository.findById(ID)).thenReturn(Optional.of(tag));
    when(tagQueryService.countMap(List.of(ID))).thenReturn(Map.of());

    useCase.execute(USER, ID, "same", null);

    verify(tagRepository, never()).findFirstByUserIdAndName(USER, "same");
    verify(tag).rename("same");
  }

  @Test
  void recolorsWhenOnlyColorProvided() {
    TagEntity tag = ownedTag("old", null);
    when(tagRepository.findById(ID)).thenReturn(Optional.of(tag));
    when(tagQueryService.countMap(List.of(ID))).thenReturn(Map.of(ID, 0L));

    useCase.execute(USER, ID, null, "#AABBCC");

    verify(tag).recolor("#aabbcc");
    verify(tag, never()).rename(anyString());
  }

  @Test
  void leavesTagUnchangedWhenNoFieldsProvided() {
    TagEntity tag = ownedTag("old", "#112233");
    when(tagRepository.findById(ID)).thenReturn(Optional.of(tag));
    when(tagQueryService.countMap(List.of(ID))).thenReturn(Map.of(ID, 2L));

    TagSummary summary = useCase.execute(USER, ID, null, null);

    verify(tag, never()).rename(anyString());
    verify(tag, never()).recolor(anyString());
    assertThat(summary.linkCount()).isEqualTo(2L);
  }
}
