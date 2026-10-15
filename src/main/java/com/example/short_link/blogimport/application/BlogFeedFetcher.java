package com.example.short_link.blogimport.application;

// Reads a public http(s) document. Throws BlogImportException: IMPORT_FEED_NOT_FOUND when the
// address answers 404 or 410, IMPORT_SOURCE_UNREACHABLE for every other failure. Calls the network;
// never call it inside a transaction.
public interface BlogFeedFetcher {

  String fetch(String url);
}
