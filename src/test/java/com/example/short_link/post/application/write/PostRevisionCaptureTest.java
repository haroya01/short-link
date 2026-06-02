package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostRevisionEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostRevisionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostRevisionCaptureTest {

  @Mock private PostRevisionRepository postRevisionRepository;
  @Mock private PostBlockRepository postBlockRepository;
  @InjectMocks private PostRevisionCapture capture;

  private static final long POST_ID = 100L;

  private PostEntity post() {
    PostEntity post = mock(PostEntity.class);
    lenient().when(post.getId()).thenReturn(POST_ID);
    lenient().when(post.getTitle()).thenReturn("My Title");
    lenient().when(post.getExcerpt()).thenReturn("excerpt");
    lenient().when(post.getOgImageUrl()).thenReturn(null);
    lenient().when(post.getOgImageKey()).thenReturn(null);
    lenient().when(post.getLanguageTag()).thenReturn("ko");
    return post;
  }

  @Test
  void capturesFirstRevisionWhenNoneExist() {
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(POST_ID)).thenReturn(List.of());
    when(postRevisionRepository.findLatestByPostId(POST_ID)).thenReturn(Optional.empty());
    when(postRevisionRepository.save(any(PostRevisionEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    capture.capture(post());

    ArgumentCaptor<PostRevisionEntity> saved = ArgumentCaptor.forClass(PostRevisionEntity.class);
    verify(postRevisionRepository).save(saved.capture());
    assertThat(saved.getValue().getVersionNumber()).isEqualTo(1);
    assertThat(saved.getValue().getTitleSnapshot()).isEqualTo("My Title");
  }

  @Test
  void incrementsVersionAndSnapshotsBlocks() {
    PostBlockEntity block = new PostBlockEntity(POST_ID, PostBlockType.PARAGRAPH, "hello", 0);
    PostRevisionEntity previous = mock(PostRevisionEntity.class);
    when(previous.getVersionNumber()).thenReturn(3);
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(POST_ID))
        .thenReturn(List.of(block));
    when(postRevisionRepository.findLatestByPostId(POST_ID)).thenReturn(Optional.of(previous));
    when(postRevisionRepository.save(any(PostRevisionEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    capture.capture(post());

    ArgumentCaptor<PostRevisionEntity> saved = ArgumentCaptor.forClass(PostRevisionEntity.class);
    verify(postRevisionRepository).save(saved.capture());
    assertThat(saved.getValue().getVersionNumber()).isEqualTo(4);
    // 본문 블록이 snapshot JSON 에 직렬화됐는지 — 내용 일부로 확인.
    assertThat(saved.getValue().getContentJson()).contains("hello").contains("PARAGRAPH");
  }
}
