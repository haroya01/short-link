package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.domain.PostEntity;
import org.junit.jupiter.api.Test;

class PublicPostListItemTest {

  @Test
  void aChosenCoverIsMarkedSoTheReaderCanShowItAsTheHero() {
    PostEntity post = new PostEntity(7L, "picked", "Picked", "ko");
    post.updateOgImage("https://cdn/photo.png", null, true);

    PublicPostListItem item = PublicPostListItem.from(post);

    assertThat(item.coverChosen()).isTrue();
    assertThat(item.thumbnailUrl()).isEqualTo("https://cdn/photo.png");
  }

  @Test
  void aCoverFilledInFromTheBodyIsNotMarked() {
    PostEntity post = new PostEntity(7L, "filled", "Filled", "ko");
    post.updateOgImage("https://cdn/body-first.png", null, false);

    PublicPostListItem item = PublicPostListItem.from(post);

    assertThat(item.coverChosen()).isFalse();
    assertThat(item.ogImageUrl()).isEqualTo("https://cdn/body-first.png");
    assertThat(item.thumbnailUrl()).isNull();
  }
}
