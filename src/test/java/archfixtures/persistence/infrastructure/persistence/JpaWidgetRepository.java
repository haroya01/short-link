package archfixtures.persistence.infrastructure.persistence;

import archfixtures.persistence.domain.WidgetEntity;
import archfixtures.persistence.domain.repository.WidgetRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaWidgetRepository extends JpaRepository<WidgetEntity, Long>, WidgetRepository {}
