package com.example.short_link.link.destination.infrastructure.persistence;

import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaLinkDestinationRepository extends JpaRepository<LinkDestinationEntity, Long> {

  List<LinkDestinationEntity> findAllByLinkIdOrderByIdAsc(Long linkId);

  long countByLinkId(Long linkId);
}
