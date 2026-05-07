package com.example.short_link.link.api;

import java.util.List;

public record MyLinksPage(List<MyLinkResponse> items, long total) {}
