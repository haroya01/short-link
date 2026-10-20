package com.example.short_link.post.application.read;

import java.util.List;

public record PostSlice(List<At> items, boolean exhausted) {

  public record At(int position, PostCard post) {}
}
