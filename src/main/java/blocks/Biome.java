package blocks;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class Biome {
  private final BiomeType type;
  private final List<Vec2> cells;
  private final List<Vec2> polygon;
  private final List<Biome> neighbors;

  public BiomeType type() {
    return type;
  }

  public List<Vec2> cells() {
    return cells;
  }

  public List<Vec2> polygon() {
    return polygon;
  }

  public List<Biome> neighbors() {
    return neighbors;
  }

  public Biome(BiomeType type, List<Vec2> cells, List<Vec2> polygon, List<Biome> neighbors) {
    this.type = type;
    this.cells = cells;
    this.polygon = polygon;
    this.neighbors = neighbors;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Biome biome = (Biome) o;
    return type == biome.type && cells.equals(biome.cells) && polygon.equals(biome.polygon);
  }

  // for debugging
  private static final Iterator<Integer> nextId = Stream.iterate(0, x -> x + 1).iterator();
  public int id = Biome.nextId.next();

  @Override
  public String toString() {
    String neighborsString =
        neighbors.stream().map(x -> x.getClass().getSimpleName() + '@' + x.id).toList().toString();
    return String.format(
        "Biome[id=%s, type=%s, cells=%s, polygon=%s, neighbors=%s]",
        id, type, cells, polygon, neighborsString);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, cells, polygon);
  }
}
