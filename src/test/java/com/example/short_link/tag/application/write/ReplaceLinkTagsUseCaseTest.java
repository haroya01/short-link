package com.example.short_link.tag.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.tag.domain.LinkTagEntity;
import com.example.short_link.tag.domain.TagEntity;
import com.example.short_link.tag.domain.repository.LinkTagRepository;
import com.example.short_link.tag.domain.repository.TagRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReplaceLinkTagsUseCaseTest {

  @Mock private LinkRepository linkRepository;
  @Mock private TagRepository tagRepository;
  @Mock private LinkTagRepository linkTagRepository;
  @InjectMocks private ReplaceLinkTagsUseCase useCase;

  private static final long USER = 7L;
  private static final ShortCode CODE = new ShortCode("abcde1");

  private LinkEntity ownedLink() {
    LinkEntity link = mock(LinkEntity.class);
    lenient().when(link.isOwnedBy(USER)).thenReturn(true);
    lenient().when(link.linkId()).thenReturn(new LinkId(100L));
    return link;
  }

  private TagEntity tag(long id, String name) {
    TagEntity t = mock(TagEntity.class);
    lenient().when(t.getId()).thenReturn(id);
    lenient().when(t.getName()).thenReturn(name);
    return t;
  }

  @Test
  void throwsWhenLinkNotFound() {
    when(linkRepository.findByShortCode(CODE)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(USER, CODE, List.of("a")))
        .isInstanceOf(LinkException.class);
  }

  @Test
  void throwsWhenLinkNotOwned() {
    LinkEntity link = ownedLink();
    when(link.isOwnedBy(USER)).thenReturn(false);
    when(linkRepository.findByShortCode(CODE)).thenReturn(Optional.of(link));

    assertThatThrownBy(() -> useCase.execute(USER, CODE, List.of("a")))
        .isInstanceOf(LinkException.class);
  }

  @Test
  void throwsWhenTooManyTags() {
    LinkEntity link = ownedLink();
    when(linkRepository.findByShortCode(CODE)).thenReturn(Optional.of(link));
    List<String> tooMany = IntStream.rangeClosed(1, 21).mapToObj(i -> "t" + i).toList();

    assertThatThrownBy(() -> useCase.execute(USER, CODE, tooMany))
        .isInstanceOf(IllegalArgumentException.class);

    verify(linkTagRepository, never()).deleteByLinkId(any(Long.class));
  }

  @Test
  void throwsWhenTagNameTooLong() {
    LinkEntity link = ownedLink();
    when(linkRepository.findByShortCode(CODE)).thenReturn(Optional.of(link));
    String tooLong = "x".repeat(51);

    assertThatThrownBy(() -> useCase.execute(USER, CODE, List.of(tooLong)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void clearsAllTagsWhenEmpty() {
    LinkEntity link = ownedLink();
    when(linkRepository.findByShortCode(CODE)).thenReturn(Optional.of(link));

    List<String> result = useCase.execute(USER, CODE, List.of());

    assertThat(result).isEmpty();
    verify(linkTagRepository).deleteByLinkId(100L);
    verify(linkTagRepository, never()).save(any());
  }

  @Test
  void reusesExistingTagsAndCreatesMissingOnes() {
    LinkEntity link = ownedLink();
    TagEntity existing = tag(1L, "java");
    TagEntity created = tag(2L, "go");
    when(linkRepository.findByShortCode(CODE)).thenReturn(Optional.of(link));
    when(tagRepository.findAllByUserIdAndNameIn(USER, List.of("java", "go")))
        .thenReturn(List.of(existing));
    when(tagRepository.save(any(TagEntity.class))).thenReturn(created);

    List<String> result = useCase.execute(USER, CODE, List.of("java", "go"));

    assertThat(result).containsExactly("java", "go");
    // "go" is missing → created once; "java" already exists → not created.
    verify(tagRepository).save(any(TagEntity.class));
    verify(linkTagRepository, times(2)).save(any(LinkTagEntity.class));
  }

  @Test
  void normalizesByTrimmingDedupingAndDroppingBlanks() {
    LinkEntity link = ownedLink();
    TagEntity created = tag(5L, "java");
    when(linkRepository.findByShortCode(CODE)).thenReturn(Optional.of(link));
    when(tagRepository.findAllByUserIdAndNameIn(USER, List.of("java"))).thenReturn(List.of());
    when(tagRepository.save(any(TagEntity.class))).thenReturn(created);

    List<String> raw = new ArrayList<>();
    raw.add("java");
    raw.add(" java ");
    raw.add("");
    raw.add(null);

    List<String> result = useCase.execute(USER, CODE, raw);

    assertThat(result).containsExactly("java");
  }
}
