package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.link.application.TagService.TagSummary;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.link.exception.DuplicateTagNameException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TagServiceTest {

  @Autowired private TagService tagService;
  @Autowired private LinkTagService linkTagService;
  @Autowired private UserRepository userRepository;
  @Autowired private LinkRepository linkRepository;

  @Test
  void createListUpdateDelete() {
    UserEntity user = userRepository.save(new UserEntity("tag1@example.com", "google", "g-tag1"));

    TagSummary work = tagService.create(user.getId(), "work", "#0066cc");
    TagSummary personal = tagService.create(user.getId(), "personal", null);

    List<TagSummary> listed = tagService.list(user.getId());
    assertThat(listed).extracting(TagSummary::name).containsExactly("personal", "work");

    TagSummary renamed = tagService.update(user.getId(), work.id(), "office", "#ff0000");
    assertThat(renamed.name()).isEqualTo("office");
    assertThat(renamed.color()).isEqualTo("#ff0000");

    tagService.delete(user.getId(), personal.id());
    assertThat(tagService.list(user.getId())).hasSize(1);
  }

  @Test
  void duplicateNameRejected() {
    UserEntity user = userRepository.save(new UserEntity("tag2@example.com", "google", "g-tag2"));
    tagService.create(user.getId(), "shared", null);
    assertThatThrownBy(() -> tagService.create(user.getId(), "shared", null))
        .isInstanceOf(DuplicateTagNameException.class);
  }

  @Test
  void replaceTagsAutoCreatesAndCounts() {
    UserEntity user = userRepository.save(new UserEntity("tag3@example.com", "google", "g-tag3"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "tagged01", user.getId(), null));

    List<String> applied =
        linkTagService.replaceTags(user.getId(), "tagged01", List.of("alpha", "beta", "alpha"));
    assertThat(applied).containsExactly("alpha", "beta");

    List<String> reloaded = linkTagService.tagNamesFor(user.getId(), "tagged01");
    assertThat(reloaded).containsExactly("alpha", "beta");

    List<TagSummary> tags = tagService.list(user.getId());
    assertThat(tags).extracting(TagSummary::name).containsExactly("alpha", "beta");
    assertThat(tags).extracting(TagSummary::linkCount).containsExactly(1L, 1L);

    linkTagService.replaceTags(user.getId(), "tagged01", List.of("alpha"));
    assertThat(linkTagService.tagNamesFor(user.getId(), "tagged01")).containsExactly("alpha");
  }
}
