package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.TagPrefKind;
import com.example.short_link.post.domain.UserTagPrefEntity;
import com.example.short_link.post.domain.repository.UserTagPrefRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TagPrefQueryServiceTest {

  @Mock private UserTagPrefRepository repository;

  private TagPrefQueryService service;

  @BeforeEach
  void setUp() {
    service = new TagPrefQueryService(repository);
  }

  @Test
  void partitionsByKind() {
    when(repository.findAllByUserId(9L))
        .thenReturn(
            List.of(
                new UserTagPrefEntity(9L, "개발", TagPrefKind.FOLLOW),
                new UserTagPrefEntity(9L, "디자인", TagPrefKind.FOLLOW),
                new UserTagPrefEntity(9L, "광고", TagPrefKind.HIDE)));

    TagPrefsView view = service.get(9L);

    assertThat(view.followed()).containsExactly("개발", "디자인");
    assertThat(view.hidden()).containsExactly("광고");
  }
}
