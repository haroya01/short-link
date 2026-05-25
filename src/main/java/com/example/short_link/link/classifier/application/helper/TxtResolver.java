package com.example.short_link.link.classifier.application.helper;

import java.util.List;

/**
 * Resolves TXT records for custom-domain verification. Production uses JNDI against public
 * resolvers; tests swap in a stub so the verify flow can be exercised without real DNS.
 *
 * <p>Implementations must never throw — DNS misses are the normal "still propagating" path, so
 * lookup failures should be surfaced as an empty list.
 */
public interface TxtResolver {

  List<String> lookup(String host);
}
