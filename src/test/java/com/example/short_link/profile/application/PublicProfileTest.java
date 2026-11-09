package com.example.short_link.profile.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.profile.application.PublicProfile.ProfileEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublicProfileTest {

  @Test
  void linkEntryCarriesShortCodeAndUrlsAndCount() {
    ProfileEntry e =
        ProfileEntry.link(
            new ShortCode("abc"), "https://k.url/abc", "https://x.com", "title", "img", 12L, true);
    assertThat(e.kind()).isEqualTo("LINK");
    assertThat(e.shortCode().value()).isEqualTo("abc");
    assertThat(e.shortUrl()).isEqualTo("https://k.url/abc");
    assertThat(e.originalUrl()).isEqualTo("https://x.com");
    assertThat(e.ogTitle()).isEqualTo("title");
    assertThat(e.ogImage()).isEqualTo("img");
    assertThat(e.clickCount()).isEqualTo(12L);
    assertThat(e.highlighted()).isTrue();
    assertThat(e.id()).isNull();
    assertThat(e.content()).isNull();
  }

  @Test
  void textEntryHoldsIdAndContent() {
    ProfileEntry e = ProfileEntry.text(7L, "hello");
    assertThat(e.kind()).isEqualTo("TEXT");
    assertThat(e.id()).isEqualTo(7L);
    assertThat(e.content()).isEqualTo("hello");
    assertThat(e.shortCode()).isNull();
  }

  @Test
  void dividerEntryHasOnlyId() {
    ProfileEntry e = ProfileEntry.divider(3L);
    assertThat(e.kind()).isEqualTo("DIVIDER");
    assertThat(e.id()).isEqualTo(3L);
    assertThat(e.content()).isNull();
  }

  @Test
  void imageEntryStoresUrlInContent() {
    ProfileEntry e = ProfileEntry.image(1L, "https://cdn/a.png");
    assertThat(e.kind()).isEqualTo("IMAGE");
    assertThat(e.content()).isEqualTo("https://cdn/a.png");
  }

  @Test
  void embedEntryStoresUrlInContent() {
    ProfileEntry e = ProfileEntry.embed(1L, "https://youtu.be/x");
    assertThat(e.kind()).isEqualTo("EMBED");
    assertThat(e.content()).isEqualTo("https://youtu.be/x");
  }

  @Test
  void emailFormEntryCarriesConfig() {
    ProfileEntry e = ProfileEntry.emailForm(1L, "{}");
    assertThat(e.kind()).isEqualTo("EMAIL_FORM");
    assertThat(e.content()).isEqualTo("{}");
  }

  @Test
  void contactCardEntryCarriesConfig() {
    ProfileEntry e = ProfileEntry.contactCard(1L, "{\"name\":\"x\"}");
    assertThat(e.kind()).isEqualTo("CONTACT_CARD");
    assertThat(e.content()).isEqualTo("{\"name\":\"x\"}");
  }

  @Test
  void galleryEntryCarriesConfig() {
    ProfileEntry e = ProfileEntry.gallery(1L, "{\"images\":[]}");
    assertThat(e.kind()).isEqualTo("GALLERY");
    assertThat(e.content()).isEqualTo("{\"images\":[]}");
  }

  @Test
  void productCardEntryCarriesConfig() {
    ProfileEntry e = ProfileEntry.productCard(1L, "{\"items\":[]}", true);
    assertThat(e.kind()).isEqualTo("PRODUCT_CARD");
    assertThat(e.content()).isEqualTo("{\"items\":[]}");
    assertThat(e.highlighted()).isTrue();
  }

  @Test
  void bookingEntryCarriesConfig() {
    ProfileEntry e = ProfileEntry.booking(1L, "{\"url\":\"x\"}");
    assertThat(e.kind()).isEqualTo("BOOKING");
    assertThat(e.content()).isEqualTo("{\"url\":\"x\"}");
  }

  @Test
  void eventEntryCarriesConfig() {
    ProfileEntry e = ProfileEntry.event(1L, "{\"title\":\"x\"}", false);
    assertThat(e.kind()).isEqualTo("EVENT");
    assertThat(e.content()).isEqualTo("{\"title\":\"x\"}");
    assertThat(e.highlighted()).isFalse();
  }

  @Test
  void placeEntryCarriesConfig() {
    ProfileEntry e = ProfileEntry.place(1L, "{\"name\":\"x\"}");
    assertThat(e.kind()).isEqualTo("PLACE");
    assertThat(e.content()).isEqualTo("{\"name\":\"x\"}");
  }

  @Test
  void publicProfileRecordExposesFields() {
    PublicProfile p =
        new PublicProfile("alice", "bio", "dark", "a.png", "b.png", List.of(), List.of(), 3L, true);
    assertThat(p.username()).isEqualTo("alice");
    assertThat(p.bio()).isEqualTo("bio");
    assertThat(p.theme()).isEqualTo("dark");
    assertThat(p.avatarUrl()).isEqualTo("a.png");
    assertThat(p.bannerUrl()).isEqualTo("b.png");
    assertThat(p.socials()).isEmpty();
    assertThat(p.entries()).isEmpty();
    assertThat(p.publishedPostCount()).isEqualTo(3L);
    assertThat(p.hideFollowerCount()).isTrue();
  }

  @Test
  void everyBlockEntryCarriesItsKindIdAndContentAndOnlyCardsAndEventsAHighlight() {
    assertThat(
            List.of(
                ProfileEntry.text(1L, "t"),
                ProfileEntry.divider(2L),
                ProfileEntry.image(3L, "i"),
                ProfileEntry.embed(4L, "e"),
                ProfileEntry.emailForm(5L, "f"),
                ProfileEntry.contactCard(6L, "c"),
                ProfileEntry.gallery(7L, "g"),
                ProfileEntry.productCard(8L, "p", true),
                ProfileEntry.booking(9L, "b"),
                ProfileEntry.event(10L, "v", false),
                ProfileEntry.place(11L, "l")))
        .containsExactly(
            block("TEXT", 1L, null, "t"),
            block("DIVIDER", 2L, null, null),
            block("IMAGE", 3L, null, "i"),
            block("EMBED", 4L, null, "e"),
            block("EMAIL_FORM", 5L, null, "f"),
            block("CONTACT_CARD", 6L, null, "c"),
            block("GALLERY", 7L, null, "g"),
            block("PRODUCT_CARD", 8L, true, "p"),
            block("BOOKING", 9L, null, "b"),
            block("EVENT", 10L, false, "v"),
            block("PLACE", 11L, null, "l"));
  }

  private static ProfileEntry block(String kind, Long id, Boolean highlighted, String content) {
    return new ProfileEntry(
        kind, id, null, null, null, null, null, null, highlighted, content, null);
  }
}
