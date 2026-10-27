package archfixtures.modules.beta.application;

import archfixtures.modules.alpha.application.AlphaService;
import archfixtures.modules.alpha.domain.AlphaEntity;
import archfixtures.modules.alpha.domain.AlphaId;
import archfixtures.modules.alpha.domain.repository.AlphaRepository;
import archfixtures.modules.alpha.infrastructure.AlphaStore;

public final class ModuleFixtures {

  private ModuleFixtures() {}

  public static class ReachesAnotherModulesStore {
    public String run() {
      return new AlphaStore().read();
    }
  }

  public static class ReachesAnotherModulesRepository {
    public String run(AlphaRepository alpha) {
      return alpha.find();
    }
  }

  public static class ReachesAnotherModulesEntity {
    public Long run(AlphaEntity alpha) {
      return alpha.getId();
    }
  }

  public static class PassesAnotherModulesValue {
    public long run(AlphaId alpha) {
      return alpha.value();
    }
  }

  public static class GoesThroughAnotherModulesService {
    public String run(AlphaService alpha) {
      return alpha.read();
    }
  }
}
