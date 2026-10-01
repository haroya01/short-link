package com.example.short_link.link.destination.domain.repository;

import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LinkDestinationRepository {

  Optional<LinkDestinationEntity> findById(Long id);

  LinkDestinationEntity save(LinkDestinationEntity destination);

  void delete(LinkDestinationEntity destination);

  List<LinkDestinationEntity> findAllByLinkIdOrderByIdAsc(Long linkId);

  List<LinkDestinationEntity> findAllByLinkIdIn(Collection<Long> linkIds);

  long countByLinkId(Long linkId);
}
