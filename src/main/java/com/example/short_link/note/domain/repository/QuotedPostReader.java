package com.example.short_link.note.domain.repository;

import com.example.short_link.note.domain.QuotedPost;
import java.util.Collection;
import java.util.Map;

public interface QuotedPostReader {

  Map<Long, QuotedPost> publishedByIds(Collection<Long> postIds);
}
