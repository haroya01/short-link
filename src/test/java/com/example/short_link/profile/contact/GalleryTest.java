package com.example.short_link.profile.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.profile.application.InvalidUsernameException;
import org.junit.jupiter.api.Test;

class GalleryTest {

  @Test
  void normalizesValidGallery() {
    String out =
        Gallery.normalize("{\"images\":[\"https://a.example/1.jpg\",\"https://b.example/2.png\"]}");
    assertThat(out).contains("https://a.example/1.jpg");
    assertThat(out).contains("https://b.example/2.png");
  }

  @Test
  void requiresAtLeastOneImage() {
    assertThatThrownBy(() -> Gallery.normalize("{\"images\":[]}"))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(() -> Gallery.normalize("{\"images\":[\"  \",\"\"]}"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void enforcesMaxImagesCap() {
    StringBuilder json = new StringBuilder("{\"images\":[");
    for (int i = 0; i < Gallery.MAX_IMAGES + 1; i++) {
      if (i > 0) json.append(',');
      json.append("\"https://example.com/").append(i).append(".jpg\"");
    }
    json.append("]}");
    assertThatThrownBy(() -> Gallery.normalize(json.toString()))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void rejectsNonHttpScheme() {
    assertThatThrownBy(() -> Gallery.normalize("{\"images\":[\"file:///etc/passwd\"]}"))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(() -> Gallery.normalize("{\"images\":[\"javascript:alert(1)\"]}"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void rejectsMalformedJson() {
    assertThatThrownBy(() -> Gallery.normalize("not json"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void dropsBlankEntries() {
    String out =
        Gallery.normalize(
            "{\"images\":[\"https://a.example/1.jpg\",\"   \",\"https://b.example/2.png\"]}");
    assertThat(out).contains("1.jpg");
    assertThat(out).contains("2.png");
  }

  @Test
  void ignoresUnknownFields() {
    // Forward compat — same class of bug as ContactCard logoFocalX/Y hotfix (PR #256). A frontend
    // sending v2 fields (captions[], aspectRatio) before backend deploy shouldn't 400 the save.
    String out =
        Gallery.normalize(
            "{\"images\":[\"https://a.example/1.jpg\"],"
                + "\"captions\":[\"hello\"],\"aspectRatio\":\"16:9\"}");
    assertThat(out).contains("1.jpg");
  }
}
