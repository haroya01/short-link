package com.example.short_link.profile.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.profile.application.Socials.Social;
import java.util.List;
import org.junit.jupiter.api.Test;

class MyProfileTest {

  @Test
  void recordExposesAllFields() {
    List<Social> socials = List.of(new Social("x", "https://x.com/me"));
    MyProfile p =
        new MyProfile(
            "alice", "bio", "dark", "https://kurl/u/alice", "avatar.png", "banner.png", socials);
    assertThat(p.username()).isEqualTo("alice");
    assertThat(p.bio()).isEqualTo("bio");
    assertThat(p.theme()).isEqualTo("dark");
    assertThat(p.publicUrl()).isEqualTo("https://kurl/u/alice");
    assertThat(p.avatarUrl()).isEqualTo("avatar.png");
    assertThat(p.bannerUrl()).isEqualTo("banner.png");
    assertThat(p.socials()).hasSize(1);
  }

  @Test
  void nullableFieldsAllowed() {
    MyProfile p = new MyProfile(null, null, null, null, null, null, List.of());
    assertThat(p.username()).isNull();
    assertThat(p.socials()).isEmpty();
  }
}
