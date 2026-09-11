package com.example.short_link.post.collection.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CollectionEntityTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "\t\n"})
  void rejectsMissingTitleAtCreation(String title) {
    assertThatThrownBy(
            () ->
                new CollectionEntity(
                    1L, title, "설명", CollectionVisibility.PRIVATE, CollectionKind.PATH))
        .isInstanceOf(PostException.class)
        .extracting(error -> ((PostException) error).errorCode())
        .isEqualTo(PostErrorCode.COLLECTION_TITLE_REQUIRED);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "\t\n"})
  void rejectsMissingTitleBeforeChangingExistingFields(String title) {
    CollectionEntity collection =
        new CollectionEntity(
            1L, "처음 제목", "처음 설명", CollectionVisibility.PRIVATE, CollectionKind.PATH);

    assertThatThrownBy(() -> collection.edit(title, "바뀔 설명", CollectionVisibility.PUBLIC))
        .isInstanceOf(PostException.class)
        .extracting(error -> ((PostException) error).errorCode())
        .isEqualTo(PostErrorCode.COLLECTION_TITLE_REQUIRED);

    assertThat(collection.getTitle()).isEqualTo("처음 제목");
    assertThat(collection.getDescription()).isEqualTo("처음 설명");
    assertThat(collection.getVisibility()).isEqualTo(CollectionVisibility.PRIVATE);
    assertThat(collection.getKind()).isEqualTo(CollectionKind.PATH);
  }

  @Test
  void creationAndEditShareWhitespaceAndLengthRules() {
    String title = "  " + "제".repeat(150) + "  ";
    String description = "\t" + "설".repeat(300) + "\n";
    CollectionEntity collection =
        new CollectionEntity(1L, title, description, CollectionVisibility.PRIVATE, null);

    assertThat(collection.getTitle()).isEqualTo("제".repeat(120));
    assertThat(collection.getDescription()).isEqualTo("설".repeat(280));
    assertThat(collection.getKind()).isEqualTo(CollectionKind.COLLECTION);

    collection.edit("  짧은 제목  ", "  짧은 설명  ", CollectionVisibility.PUBLIC);
    assertThat(collection.getTitle()).isEqualTo("짧은 제목");
    assertThat(collection.getDescription()).isEqualTo("짧은 설명");

    collection.edit(title, description, CollectionVisibility.PRIVATE);
    assertThat(collection.getTitle()).isEqualTo("제".repeat(120));
    assertThat(collection.getDescription()).isEqualTo("설".repeat(280));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "\t\n"})
  void optionalDescriptionUsesNullForMissingText(String description) {
    CollectionEntity collection =
        new CollectionEntity(
            1L, "제목", description, CollectionVisibility.PRIVATE, CollectionKind.PATH);
    assertThat(collection.getDescription()).isNull();

    collection.edit("제목", "이전 설명", CollectionVisibility.PRIVATE);
    collection.edit("제목", description, CollectionVisibility.PRIVATE);
    assertThat(collection.getDescription()).isNull();
  }
}
