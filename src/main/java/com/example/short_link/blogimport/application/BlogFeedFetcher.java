package com.example.short_link.blogimport.application;

import com.example.short_link.blogimport.domain.FeedValidators;
import java.util.Optional;

// Reads public http(s) documents over HTTP/1.1. fetch throws BlogImportException:
// IMPORT_FEED_NOT_FOUND when the address answers 404 or 410, IMPORT_SOURCE_UNREACHABLE for every
// other failure; with validators it asks conditionally and a 304 comes back as notModified.
// robots returns the robots.txt body, empty when the site has none (any 4xx), and throws
// IMPORT_SOURCE_UNREACHABLE when it cannot be read. Calls the network; never call it inside a
// transaction.
public interface BlogFeedFetcher {

  Fetched fetch(String url, FeedValidators validators);

  Optional<String> robots(String origin);

  record Fetched(String body, FeedValidators validators, boolean notModified) {

    public static Fetched notModified(FeedValidators validators) {
      return new Fetched(null, validators, true);
    }
  }
}
