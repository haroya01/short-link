package archfixtures.persistence.domain.repository;

import archfixtures.persistence.domain.WidgetEntity;
import java.util.Optional;

public interface WidgetRepository {

  Optional<WidgetEntity> findById(Long id);
}
