package com.example.short_link.post.application.write;

import com.example.short_link.post.application.read.PostView;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 쓰기 트랜잭션 안에서 저장 시각과 지연 로딩 데이터를 확정한 뒤 응답을 만든다. */
@Component
@RequiredArgsConstructor
public class PostWriteViewAssembler {
  private final PostRepository postRepository;

  public PostView fromSaved(PostEntity post) {
    // @UpdateTimestamp는 flush 시 갱신된다. 먼저 DTO로 복사하면 이전 수정 시각이 응답에 남는다.
    postRepository.flush();
    return PostView.from(post);
  }
}
