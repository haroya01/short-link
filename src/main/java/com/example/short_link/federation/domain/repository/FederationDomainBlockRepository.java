package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.FederationDomainBlockEntity;
import java.util.List;
import java.util.Optional;

public interface FederationDomainBlockRepository {

  List<FederationDomainBlockEntity> list();

  Optional<FederationDomainBlockEntity> find(String domain);

  boolean suspends(String host);

  FederationDomainBlockEntity save(FederationDomainBlockEntity block);

  int delete(String domain);

  // Members' follows of the server's accounts, its accounts' follows of members, and activities
  // still waiting to go there.
  void sever(String domain);
}
