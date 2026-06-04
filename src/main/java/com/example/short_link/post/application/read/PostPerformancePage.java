package com.example.short_link.post.application.read;

import java.util.List;

/**
 * One page of the author's per-post performance table, ordered by lifetime views. Paginated so the
 * analytics overview can lazy-load (infinite scroll) instead of pulling every post at once.
 *
 * @param items the page's rows (reuses {@link TopPostView}: views·likes·follows per post)
 * @param page zero-based page index this response represents
 * @param hasNext whether another page exists after this one
 */
public record PostPerformancePage(List<TopPostView> items, int page, boolean hasNext) {}
