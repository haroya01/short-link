package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.AuthorPostNumberRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostNumbering {

  // 클라이언트가 지어 내는 임시 주소: 웹 randomSlug 의 "draft-" + base36 최대 7자, iOS createDraft 의
  // "p-<epoch 초>-<세 자리>". 옛 앱도 이 모양으로 보낸다.
  private static final Pattern CLIENT_MADE =
      Pattern.compile("^(?:draft-[0-9a-z]{1,7}|p-\\d{9,11}-\\d{3})$");

  private final AuthorPostNumberRepository numbers;
  private final PostRepository posts;

  public void numberIfClientMade(PostEntity post) {
    if (post.getPublishedAt() != null || !CLIENT_MADE.matcher(post.getSlug()).matches()) {
      return;
    }
    long number = numbers.next(post.getUserId());
    while (posts.existsByUserIdAndSlug(post.getUserId(), String.valueOf(number))) {
      number = numbers.next(post.getUserId());
    }
    post.numberSlug(number);
  }
}
