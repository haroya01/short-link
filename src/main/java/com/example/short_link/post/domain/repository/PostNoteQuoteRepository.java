package com.example.short_link.post.domain.repository;

import java.util.Collection;

public interface PostNoteQuoteRepository {

  void add(Long postId, Collection<Long> noteIds);

  void replace(Long postId, Collection<Long> noteIds);
}
