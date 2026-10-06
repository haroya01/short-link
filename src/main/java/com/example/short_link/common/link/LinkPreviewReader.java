package com.example.short_link.common.link;

import java.util.Optional;

// Open Graph card for a public http(s) address. Empty when the page has neither a title nor an
// image worth showing, or when the address is not public.
public interface LinkPreviewReader {

  Optional<Preview> read(String url);

  record Preview(String url, String title, String description, String image) {}
}
