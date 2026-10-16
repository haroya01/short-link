package archfixtures;

import com.example.short_link.post.application.access.PostAccessPolicy;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;

public final class PostVisibilityFixtures {

  private PostVisibilityFixtures() {}

  public static class ShowsAPostBecauseItIsPublished {
    boolean shown(PostEntity post) {
      return post.isPublished();
    }
  }

  public static class ListsPostsByStatus {
    PostStatus listed() {
      return PostStatus.PUBLISHED;
    }
  }

  public static class FiltersByAMethodReference {
    java.util.List<PostEntity> shown(java.util.List<PostEntity> posts) {
      return posts.stream().filter(PostEntity::isPublished).toList();
    }
  }

  public static class AsksThePolicy {
    boolean shown(PostEntity post) {
      return PostAccessPolicy.isOpen(post);
    }
  }
}
