package com.example.short_link.profile.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.profile.application.PublicProfile.ProfileEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublicProfileTest {

  @Test
  void linkEntryCarriesShortCodeAndUrlsAndCount() {
    ProfileEntry e =
        ProfileEntry.link("abc", "https://k.url/abc", "https://x.com", "title", "img", 12L, true);
    assertThat(e.kind()).isEqualTo("LINK");
    assertThat(e.shortCode()).isEqualTo("abc");
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
    ProfileEntry e = ProfileEntry.productCard(1L, "{\"items\":[]}");
    assertThat(e.kind()).isEqualTo("PRODUCT_CARD");
    assertThat(e.content()).isEqualTo("{\"items\":[]}");
  }

  @Test
  void bookingEntryCarriesConfig() {
    ProfileEntry e = ProfileEntry.booking(1L, "{\"url\":\"x\"}");
    assertThat(e.kind()).isEqualTo("BOOKING");
    assertThat(e.content()).isEqualTo("{\"url\":\"x\"}");
  }

  @Test
  void eventEntryCarriesConfig() {
    ProfileEntry e = ProfileEntry.event(1L, "{\"title\":\"x\"}");
    assertThat(e.kind()).isEqualTo("EVENT");
    assertThat(e.content()).isEqualTo("{\"title\":\"x\"}");
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
        new PublicProfile("alice", "bio", "dark", "a.png", "b.png", List.of(), List.of());
    assertThat(p.username()).isEqualTo("alice");
    assertThat(p.bio()).isEqualTo("bio");
    assertThat(p.theme()).isEqualTo("dark");
    assertThat(p.avatarUrl()).isEqualTo("a.png");
    assertThat(p.bannerUrl()).isEqualTo("b.png");
    assertThat(p.socials()).isEmpty();
    assertThat(p.entries()).isEmpty();
  }
}
