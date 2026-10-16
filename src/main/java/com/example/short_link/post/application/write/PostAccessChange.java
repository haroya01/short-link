package com.example.short_link.post.application.write;

import java.util.List;

public record PostAccessChange(String audience, String password, List<String> blockedCountries) {

  public static final PostAccessChange NONE = new PostAccessChange(null, null, null);

  public boolean any() {
    return audience != null || password != null || blockedCountries != null;
  }
}
