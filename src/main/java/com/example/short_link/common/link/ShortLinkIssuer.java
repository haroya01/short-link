package com.example.short_link.common.link;

import java.util.Optional;

// A short link the owner keeps for an address, made through the ordinary link creation path (its
// validation and quota), reusing the owner's existing plain link to the same address. Empty when
// the address cannot be shortened for this owner. Calls the network; never call it inside a
// transaction.
public interface ShortLinkIssuer {

  Optional<Issued> issue(Long ownerId, String url);

  record Issued(long linkId, String shortCode, String shortUrl) {}
}
