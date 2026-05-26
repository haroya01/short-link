package com.example.short_link.tag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.tag.application.dto.TagSummary;
import com.example.short_link.tag.application.read.LinkTagQueryService;
import com.example.short_link.tag.application.read.TagQueryService;
import com.example.short_link.tag.application.write.CreateTagUseCase;
import com.example.short_link.tag.application.write.DeleteTagUseCase;
import com.example.short_link.tag.application.write.ReplaceLinkTagsUseCase;
import com.example.short_link.tag.application.write.UpdateTagUseCase;
import com.example.short_link.tag.exception.TagException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
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

  @Autowired private CreateTagUseCase createTag;
  @Autowired private UpdateTagUseCase updateTag;
  @Autowired private DeleteTagUseCase deleteTag;
  @Autowired private TagQueryService tagQuery;
  @Autowired private ReplaceLinkTagsUseCase replaceLinkTags;
  @Autowired private LinkTagQueryService linkTagQuery;
  @Autowired private UserRepository userRepository;
  @Autowired private LinkRepository linkRepository;

  @Test
  void createListUpdateDelete() {
    UserEntity user = userRepository.save(new UserEntity("tag1@example.com", "google", "g-tag1"));

    TagSummary work = createTag.execute(user.getId(), "work", "#0066cc");
    TagSummary personal = createTag.execute(user.getId(), "personal", null);

    List<TagSummary> listed = tagQuery.list(user.getId());
    assertThat(listed).extracting(TagSummary::name).containsExactly("personal", "work");

    TagSummary renamed = updateTag.execute(user.getId(), work.id(), "office", "#ff0000");
    assertThat(renamed.name()).isEqualTo("office");
    assertThat(renamed.color()).isEqualTo("#ff0000");

    deleteTag.execute(user.getId(), personal.id());
    assertThat(tagQuery.list(user.getId())).hasSize(1);
  }

  @Test
  void duplicateNameRejected() {
    UserEntity user = userRepository.save(new UserEntity("tag2@example.com", "google", "g-tag2"));
    createTag.execute(user.getId(), "shared", null);
    assertThatThrownBy(() -> createTag.execute(user.getId(), "shared", null))
        .isInstanceOf(TagException.class);
  }

  @Test
  void replaceTagsAutoCreatesAndCounts() {
    UserEntity user = userRepository.save(new UserEntity("tag3@example.com", "google", "g-tag3"));
    linkRepository.save(new LinkEntity("https://example.com", "tagged01", user.getId(), null));

    List<String> applied =
        replaceLinkTags.execute(
            user.getId(), new ShortCode("tagged01"), List.of("alpha", "beta", "alpha"));
    assertThat(applied).containsExactly("alpha", "beta");

    List<String> reloaded = linkTagQuery.tagNamesFor(user.getId(), new ShortCode("tagged01"));
    assertThat(reloaded).containsExactly("alpha", "beta");

    List<TagSummary> tags = tagQuery.list(user.getId());
    assertThat(tags).extracting(TagSummary::name).containsExactly("alpha", "beta");
    assertThat(tags).extracting(TagSummary::linkCount).containsExactly(1L, 1L);

    replaceLinkTags.execute(user.getId(), new ShortCode("tagged01"), List.of("alpha"));
    assertThat(linkTagQuery.tagNamesFor(user.getId(), new ShortCode("tagged01")))
        .containsExactly("alpha");
  }
}
