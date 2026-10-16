package archfixtures.post.application.write;

import com.example.short_link.post.domain.PostEntity;

public class PublishesAPost {
  boolean alreadyOut(PostEntity post) {
    return post.isPublished();
  }
}
