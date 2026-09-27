package com.example.short_link.post.collection.application.read;

import com.example.short_link.post.application.read.PublicAuthorView;

public record KindredCuratorView(PublicAuthorView curator, int sharedItems) {}
