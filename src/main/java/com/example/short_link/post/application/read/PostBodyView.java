package com.example.short_link.post.application.read;

import java.util.List;

public record PostBodyView(long contentVersion, List<PostBlockView> blocks) {}
