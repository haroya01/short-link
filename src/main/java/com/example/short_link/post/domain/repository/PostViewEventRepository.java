package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.PostViewEventEntity;

/** Append-only log of public post views — the source the trending feed windows over. */
public interface PostViewEventRepository {

  PostViewEventEntity save(PostViewEventEntity event);
}
