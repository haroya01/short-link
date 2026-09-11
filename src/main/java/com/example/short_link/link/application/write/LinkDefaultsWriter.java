package com.example.short_link.link.application.write;

import com.example.short_link.link.domain.LinkId;

/** Initializes the default associated state once, after a new link has been saved. */
public interface LinkDefaultsWriter {
  void initialize(LinkId linkId);
}
