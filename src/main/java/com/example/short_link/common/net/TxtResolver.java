package com.example.short_link.common.net;

import java.util.List;

/**
 * Lookup failures must return an empty list, never throw: DNS misses are expected while
 * verification records propagate.
 */
public interface TxtResolver {

  List<String> lookup(String host);
}
