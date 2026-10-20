package com.example.short_link.search.domain.repository;

import com.example.short_link.search.domain.RelatedTag;
import java.util.Collection;
import java.util.List;

public interface RelatedTagReader {

  List<RelatedTag> cooccurring(String match, Collection<String> words, int scan, int limit);
}
