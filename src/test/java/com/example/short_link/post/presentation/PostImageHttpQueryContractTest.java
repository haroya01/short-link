package com.example.short_link.post.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.post.application.image.ExternalPostImageReader;
import com.example.short_link.testsupport.OperationalHttpJourneySupport;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** DB ownership and HTTP flow are real; object-store and remote-image I/O are external fixtures. */
class PostImageHttpQueryContractTest extends OperationalHttpJourneySupport {
  @MockitoBean private ObjectStorage objectStorage;
  @MockitoBean private ExternalPostImageReader remoteImages;

  @Test
  void uploadsAndImportsImagesOnlyForThePersistedPostsOwner() throws Exception {
    Actor owner = actor("image-owner", false);
    Actor other = actor("image-other", false);
    when(objectStorage.isConfigured()).thenReturn(true);
    when(objectStorage.presignPut(anyString(), anyString(), any()))
        .thenReturn("https://storage.example.test/upload");
    when(objectStorage.objectSize(anyString())).thenReturn(Optional.of(4L));
    byte[] imageBytes = new byte[] {1, 2, 3, 4};
    when(remoteImages.read(anyString(), anyLong(), anyLong()))
        .thenReturn(new ExternalPostImageReader.Image(imageBytes, "image/png"));
    long postId =
        step(
                "image-create-post",
                "POST",
                "/api/v1/posts",
                owner,
                Map.of("slug", "image-post", "title", "Images", "languageTag", "ko"),
                201)
            .path("id")
            .asLong();
    String base = "/api/v1/posts/" + postId + "/images";
    String key =
        step(
                "image-presign",
                "POST",
                base + "/presign",
                owner,
                Map.of("contentType", "image/png"),
                200)
            .path("key")
            .asText();
    assertThat(key).startsWith("post-images/" + owner.id() + "/" + postId + "/");
    assertThat(
            step("image-commit", "POST", base + "/commit", owner, Map.of("key", key), 200)
                .path("key")
                .asText())
        .isEqualTo(key);
    verify(objectStorage).applyImmutableCacheControl(key);
    step("image-denies-other", "POST", base + "/commit", other, Map.of("key", key), 403);
    String importedKey =
        step(
                "image-import",
                "POST",
                base + "/import",
                owner,
                Map.of("url", "https://example.com/picture.png"),
                200)
            .path("key")
            .asText();
    verify(objectStorage).putObject(eq(importedKey), eq("image/png"), eq(imageBytes));
    assertThat(count("posts", "id = ? AND user_id = ?", postId, owner.id())).isEqualTo(1);
  }
}
