package blocks;

public enum BiomeType {
  GRASSLAND(Color.GREEN),
  DESERT(Color.YELLOW),
  OCEAN(Color.BLUE);

  private final Color color;

  BiomeType(Color color) {
    this.color = color;
  }

  public Color color() {
    return this.color;
  }
}
