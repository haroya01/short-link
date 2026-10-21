package archfixtures;

import com.example.short_link.common.post.PostAudience;
import com.example.short_link.post.application.access.PostAccessPolicy;
import com.example.short_link.post.application.access.PostReader;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRoomReader;

public final class PostAudienceFixtures {

  private PostAudienceFixtures() {}

  public static class ShowsAPostBecauseItIsPublic {
    boolean shown(PostEntity post) {
      return post.getAudience() != PostAudience.UNLISTED;
    }
  }

  public static class SwitchesOverTheAudience {
    boolean shown(PostAudience audience) {
      return switch (audience) {
        case PUBLIC, FOLLOWERS -> true;
        default -> false;
      };
    }
  }

  public static class AsksTheAudience {
    boolean shown(PostEntity post) {
      return post.getAudience().shownAsCard();
    }
  }

  public static class AsksTheRoomItself {
    boolean readable(PostRoomReader rooms, PostEntity post, Long viewerId) {
      return rooms.isMember(post.getRoomId(), viewerId);
    }
  }

  public static class AsksThePolicy {
    boolean readable(PostAccessPolicy access, PostEntity post, Long viewerId) {
      return access.readable(post, PostReader.of(viewerId));
    }
  }
}
