package com.example.short_link.profile.domain.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.profile.exception.ProfileException;
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
        .isInstanceOf(ProfileException.class);
    assertThatThrownBy(() -> Gallery.normalize("{\"images\":[\"  \",\"\"]}"))
        .isInstanceOf(ProfileException.class);
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
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void rejectsNonHttpScheme() {
    assertThatThrownBy(() -> Gallery.normalize("{\"images\":[\"file:///etc/passwd\"]}"))
        .isInstanceOf(ProfileException.class);
    assertThatThrownBy(() -> Gallery.normalize("{\"images\":[\"javascript:alert(1)\"]}"))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void rejectsMalformedJson() {
    assertThatThrownBy(() -> Gallery.normalize("not json")).isInstanceOf(ProfileException.class);
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
  void rejectsBlankInput() {
    assertThatThrownBy(() -> Gallery.normalize("  ")).isInstanceOf(ProfileException.class);
    assertThatThrownBy(() -> Gallery.normalize(null)).isInstanceOf(ProfileException.class);
  }

  @Test
  void rejectsMissingImagesField() {
    assertThatThrownBy(() -> Gallery.normalize("{}")).isInstanceOf(ProfileException.class);
  }

  @Test
  void rejectsOverlongUrl() {
    String huge = "https://example.com/" + "x".repeat(260);
    assertThatThrownBy(() -> Gallery.normalize("{\"images\":[\"" + huge + "\"]}"))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void rejectsHostlessUrl() {
    assertThatThrownBy(() -> Gallery.normalize("{\"images\":[\"https:///x.jpg\"]}"))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void rejectsSchemelessUrl() {
    assertThatThrownBy(() -> Gallery.normalize("{\"images\":[\"example.com/x.jpg\"]}"))
        .isInstanceOf(ProfileException.class);
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
