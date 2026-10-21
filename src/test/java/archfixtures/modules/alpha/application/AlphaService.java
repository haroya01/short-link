package archfixtures.modules.alpha.application;

import archfixtures.modules.alpha.infrastructure.AlphaStore;

public class AlphaService {
  private final AlphaStore store = new AlphaStore();

  public String read() {
    return store.read();
  }
}
