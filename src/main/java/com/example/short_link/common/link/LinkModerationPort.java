package com.example.short_link.common.link;

// The link slice implements this so abuse resolution can switch a link off in the same
// transaction without depending on the link slice.
public interface LinkModerationPort {

  void disable(Long adminUserId, Long linkId);
}
