package archfixtures.persistence.application;

import archfixtures.persistence.domain.WidgetEntity;
import archfixtures.persistence.domain.repository.WidgetRepository;
import archfixtures.persistence.infrastructure.persistence.JpaWidgetRepository;
import java.util.Optional;

public final class PersistenceFixtures {

  private PersistenceFixtures() {}

  public static class InjectsTheJpaRepository {
    private final JpaWidgetRepository widgets;

    public InjectsTheJpaRepository(JpaWidgetRepository widgets) {
      this.widgets = widgets;
    }

    public long count() {
      return widgets.count();
    }
  }

  public static class InjectsThePort {
    private final WidgetRepository widgets;

    public InjectsThePort(WidgetRepository widgets) {
      this.widgets = widgets;
    }

    public Optional<WidgetEntity> find(Long id) {
      return widgets.findById(id);
    }
  }
}
