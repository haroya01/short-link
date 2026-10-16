package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.post.PostLock;
import org.junit.jupiter.api.Test;

class PostLockedNoticeTest {

  @Test
  void theNoticeFollowsTheReadersLanguageAndFallsBackToKorean() {
    assertThat(PostLockedNotice.text(PostLock.FOLLOWERS, null)).isEqualTo("팔로워만 볼 수 있는 글이에요");
    assertThat(PostLockedNotice.text(PostLock.PASSWORD, "en-US,en;q=0.9"))
        .isEqualTo("This post is protected by a password.");
    assertThat(PostLockedNotice.text(PostLock.REGION, "ja")).isEqualTo("この国では読めない記事です");
    assertThat(PostLockedNotice.text(PostLock.REGION, "vi-VN")).isEqualTo("이 나라에서는 볼 수 없는 글이에요");
    assertThat(PostLockedNotice.text(PostLock.PASSWORD, ";;;")).isEqualTo("비밀번호가 걸린 글이에요");
  }

  @Test
  void aServerRenderReadsTheCountryNotice() {
    assertThat(PostLockedNotice.text(PostLock.REGION_CHECK, "ko"))
        .isEqualTo(PostLockedNotice.text(PostLock.REGION, "ko"));
  }
}
