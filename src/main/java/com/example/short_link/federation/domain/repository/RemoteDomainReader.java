package com.example.short_link.federation.domain.repository;

import java.util.Collection;
import java.util.List;

public interface RemoteDomainReader {

  // The servers this one knows the most accounts on, at most cap of them, and the named ones,
  // with how many accounts are known on each; none this server blocked.
  List<KnownDomain> known(Collection<String> named, int cap);

  record KnownDomain(String domain, long accounts) {}
}
