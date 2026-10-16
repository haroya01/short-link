package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.FederationDirectorySourceEntity;
import java.util.List;
import java.util.Optional;

public interface FederationDirectorySourceRepository {

  List<FederationDirectorySourceEntity> list();

  Optional<FederationDirectorySourceEntity> find(String host);

  FederationDirectorySourceEntity save(FederationDirectorySourceEntity source);

  void delete(FederationDirectorySourceEntity source);
}
