package com.example.short_link.post.collection.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class CollectionEntityTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "\t\n"})
  void rejectsMissingTitleAtCreation(String title) {
    assertThatThrownBy(
            () -> new CollectionEntity(1L, title, "설명", CollectionVisibility.PRIVATE, true))
        .isInstanceOf(PostException.class)
        .extracting(error -> ((PostException) error).errorCode())
        .isEqualTo(PostErrorCode.COLLECTION_TITLE_REQUIRED);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "\t\n"})
  void rejectsMissingTitleBeforeChangingExistingFields(String title) {
    CollectionEntity collection =
        new CollectionEntity(1L, "처음 제목", "처음 설명", CollectionVisibility.PRIVATE, true);

    assertThatThrownBy(() -> collection.edit(title, "바뀔 설명", CollectionVisibility.PUBLIC))
        .isInstanceOf(PostException.class)
        .extracting(error -> ((PostException) error).errorCode())
        .isEqualTo(PostErrorCode.COLLECTION_TITLE_REQUIRED);

    assertThat(collection.getTitle()).isEqualTo("처음 제목");
    assertThat(collection.getDescription()).isEqualTo("처음 설명");
    assertThat(collection.getVisibility()).isEqualTo(CollectionVisibility.PRIVATE);
    assertThat(collection.isOrdered()).isTrue();
  }

  @Test
  void theLegacyKindColumnFollowsTheOrderedFlag() {
    CollectionEntity collection =
        new CollectionEntity(1L, "제목", null, CollectionVisibility.PRIVATE, true);
    assertThat(ReflectionTestUtils.getField(collection, "kind")).isEqualTo(CollectionKind.PATH);

    collection.order(false);

    assertThat(collection.isOrdered()).isFalse();
    assertThat(ReflectionTestUtils.getField(collection, "kind"))
        .isEqualTo(CollectionKind.COLLECTION);
  }

  @Test
  void creationAndEditShareWhitespaceAndLengthRules() {
    String title = "  " + "제".repeat(150) + "  ";
    String description = "\t" + "설".repeat(300) + "\n";
    CollectionEntity collection =
        new CollectionEntity(1L, title, description, CollectionVisibility.PRIVATE, false);

    assertThat(collection.getTitle()).isEqualTo("제".repeat(120));
    assertThat(collection.getDescription()).isEqualTo("설".repeat(280));
    assertThat(collection.isOrdered()).isFalse();

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
        new CollectionEntity(1L, "제목", description, CollectionVisibility.PRIVATE, true);
    assertThat(collection.getDescription()).isNull();

    collection.edit("제목", "이전 설명", CollectionVisibility.PRIVATE);
    collection.edit("제목", description, CollectionVisibility.PRIVATE);
    assertThat(collection.getDescription()).isNull();
  }

  @Test
  void aLinkOnlyCollectionGetsAKeyThatSurvivesGoingPrivateAndBack() {
    CollectionEntity collection =
        new CollectionEntity(1L, "느린 사고", null, CollectionVisibility.PRIVATE, false);
    assertThat(collection.getUnlistedKey()).isNull();

    collection.edit("느린 사고", null, CollectionVisibility.UNLISTED);
    String key = collection.getUnlistedKey();
    collection.edit("느린 사고", null, CollectionVisibility.PRIVATE);
    collection.edit("느린 사고", null, CollectionVisibility.UNLISTED);

    assertThat(key).hasSize(32).matches("[A-Za-z0-9_-]+");
    assertThat(collection.getUnlistedKey()).isEqualTo(key);
    assertThat(
            new CollectionEntity(1L, "길", null, CollectionVisibility.UNLISTED, true)
                .getUnlistedKey())
        .hasSize(32)
        .isNotEqualTo(key);
  }

  @Test
  void othersSeeAPublicCollectionAlwaysALinkOnlyOneWithItsKeyAndAPrivateOneNever() {
    CollectionEntity collection =
        new CollectionEntity(1L, "느린 사고", null, CollectionVisibility.UNLISTED, false);
    String key = collection.getUnlistedKey();

    assertThat(collection.isVisibleTo(1L, null)).isTrue();
    assertThat(collection.isVisibleTo(2L, key)).isTrue();
    assertThat(collection.isVisibleTo(null, key)).isTrue();
    assertThat(collection.isVisibleTo(2L, null)).isFalse();
    assertThat(collection.isVisibleTo(2L, key.substring(1))).isFalse();

    collection.edit("느린 사고", null, CollectionVisibility.PRIVATE);
    assertThat(collection.isVisibleTo(2L, key)).isFalse();
    assertThat(collection.isVisibleTo(1L, null)).isTrue();

    collection.edit("느린 사고", null, CollectionVisibility.PUBLIC);
    assertThat(collection.isVisibleTo(null, null)).isTrue();
  }
}
