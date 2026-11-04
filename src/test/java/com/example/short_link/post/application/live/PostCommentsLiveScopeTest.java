package com.example.short_link.post.application.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.common.live.LiveScope;
import com.example.short_link.common.live.LiveTopic;
import com.example.short_link.common.post.PostAudience;
import com.example.short_link.post.application.access.PostAccessFixtures;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostRoomReader;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PostCommentsLiveScopeTest {

  private final PostRepository posts = mock(PostRepository.class);
  private final CommentRepository comments = mock(CommentRepository.class);
  private final PostRoomReader rooms = mock(PostRoomReader.class);
  private final PostCommentsLiveScope live =
      new PostCommentsLiveScope(posts, comments, PostAccessFixtures.policy(rooms));

  private PostEntity published(PostAudience audience, Long roomId) {
    PostEntity post = new PostEntity(7L, "slug", "Title", "ko");
    post.changeAudience(audience, roomId, audience.keyed() ? "key" : null);
    post.publish();
    when(posts.findById(5L)).thenReturn(Optional.of(post));
    return post;
  }

  @Test
  void aPublicPostOpensForAnyoneSignedInAndCarriesItsWriter() {
    published(PostAudience.PUBLIC, null);

    assertThat(live.scope()).isEqualTo(LiveTopic.Scope.POST);
    assertThat(live.open(5L, 2L, null)).contains(new LiveScope(7L, null));
  }

  @Test
  void aRoomPostOpensForMembersAndCarriesTheRoom() {
    published(PostAudience.ROOM, 9L);
    when(rooms.isMember(9L, 2L)).thenReturn(true);

    assertThat(live.open(5L, 2L, null)).contains(new LiveScope(7L, 9L));
    assertThat(live.open(5L, 3L, null)).isEmpty();
  }

  @Test
  void aPostOnlyItsKeyOpensStaysTheWritersAlone() {
    published(PostAudience.UNLISTED, null);

    assertThat(live.open(5L, 2L, null)).isEmpty();
    assertThat(live.open(5L, 7L, null)).contains(new LiveScope(7L, null));
  }

  @Test
  void aDraftOrAMissingPostIsNotThere() {
    PostEntity draft = new PostEntity(7L, "draft", "Draft", "ko");
    when(posts.findById(5L)).thenReturn(Optional.of(draft));
    when(posts.findById(6L)).thenReturn(Optional.empty());

    assertThat(live.open(5L, 7L, null)).isEmpty();
    assertThat(live.open(6L, 2L, null)).isEmpty();
  }

  @Test
  void theLatestIsTheNewestComment() {
    when(comments.latestId(5L)).thenReturn(88L);

    assertThat(live.latest(5L)).isEqualTo(88L);
  }
}
