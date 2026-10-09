package com.example.short_link.link.application.write;

import com.example.short_link.link.domain.LinkId;

// True for a link another feature created for its own use (a campaign batch's printed code); such a
// link is never handed back for an ordinary shortening of the same URL.
public interface DedicatedLinks {
  boolean isDedicated(LinkId linkId);
}
