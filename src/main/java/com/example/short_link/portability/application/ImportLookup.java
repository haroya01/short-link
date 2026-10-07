package com.example.short_link.portability.application;

import java.util.Optional;

// A note this server holds, by the address another server's export wrote for it.
public interface ImportLookup {

  Optional<Long> noteIdByUri(String uri);
}
