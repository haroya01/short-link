package archfixtures.modules.beta.application;

import archfixtures.modules.alpha.application.AlphaService;
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

  public static class GoesThroughAnotherModulesService {
    public String run(AlphaService alpha) {
      return alpha.read();
    }
  }
}
