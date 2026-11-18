package com.example.short_link.note.domain.repository;

import com.example.short_link.note.domain.QuotedPost;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface QuotedPostReader {

  Map<Long, QuotedPost> cardsFor(Long viewerId, Collection<Long> postIds);

  Optional<Long> roomOf(Long postId);
}
