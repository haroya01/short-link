package com.example.short_link.support;

import com.example.short_link.post.domain.PostEntity;

// 발견 품질 하한선(글자·숫자 100자)을 넘는 실제 같은 본문. 피드에 떠야 하는 픽스처 글이 이 문단으로 본문을 잰다.
public final class DiscoverableBodies {

  public static final String PARAGRAPH =
      "측정은 늘 같은 결론으로 돌아왔다. 요청 하나가 느릴 때 원인은 대개 한 군데에 모여 있었고 "
          + "그 한 군데를 찾는 데 가장 오래 걸렸다. 그래서 이번에는 찾는 과정 자체를 기록하기로 했다. "
          + "무엇을 먼저 의심했고 무엇이 틀렸는지까지 남겨 두면 다음에는 덜 헤맨다.";

  private DiscoverableBodies() {}

  public static PostEntity discoverable(PostEntity post) {
    post.measureBody(PARAGRAPH);
    return post;
  }
}
