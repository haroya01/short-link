package archfixtures;

public final class LongConstructorFixtures {

  private LongConstructorFixtures() {}

  public record TenParts(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j) {}

  public record NineParts(int a, int b, int c, int d, int e, int f, int g, int h, int i) {}

  public static class PassesTenArguments {
    public TenParts build() {
      return new TenParts(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
    }
  }

  public static class PassesNineArguments {
    public NineParts build() {
      return new NineParts(0, 1, 2, 3, 4, 5, 6, 7, 8);
    }
  }
}
