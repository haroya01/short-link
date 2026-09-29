package com.example.short_link.post.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// 공유 DB가 오염돼 있을 수 있어서 이 테스트가 만든 고유 표식(swf648)이 든 제목으로만 단언한다.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostSearchWordFallbackIntegrationTest {

  @Autowired private PostRepository postRepository;
  @Autowired private UserRepository userRepository;

  private Long author(String seed) {
    UserEntity u = new UserEntity(seed + "@x.com", "google", "g-" + seed);
    u.claimUsername("writer-" + seed);
    return userRepository.save(u).getId();
  }

  private void publish(Long authorId, String slug, String title) {
    PostEntity p = new PostEntity(authorId, slug, title, "ko");
    p.publish();
    postRepository.save(p);
  }

  private List<String> titlesFor(String query) {
    return postRepository.searchPublished(query, null, 0, 500).stream()
        .map(PostEntity::getTitle)
        .filter(t -> t.contains("swf648"))
        .toList();
  }

  @Test
  void twoLetterQueryMatchesTheWordButNotLettersInsideOtherWords() {
    Long a = author("swf648a");
    publish(a, "swf648-ai-ko", "AI가 바꾼 swf648 코드 리뷰");
    publish(a, "swf648-ai-en", "Notes on AI swf648");
    publish(a, "swf648-email", "Email swf648 templates");
    publish(a, "swf648-domain", "Custom domain swf648 setup");

    assertThat(titlesFor("AI"))
        .containsExactlyInAnyOrder("AI가 바꾼 swf648 코드 리뷰", "Notes on AI swf648");
  }

  @Test
  void stopwordDeadTermMatchesTheWordButNotALongerWord() {
    Long a = author("swf648b");
    publish(a, "swf648-java-ko", "Java로 서버 swf648 만들기");
    publish(a, "swf648-javascript", "JavaScript swf648 입문");

    assertThat(titlesFor("java")).containsExactly("Java로 서버 swf648 만들기");
  }
}
