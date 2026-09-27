package com.example.short_link.link.application.write;

import com.example.short_link.link.domain.LinkId;

public interface LinkDefaultsWriter {
  void initialize(LinkId linkId, String passwordHash);
}
