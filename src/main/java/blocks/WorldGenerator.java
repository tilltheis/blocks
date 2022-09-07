package blocks;

import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class WorldGenerator {
  public static final int dimension = 2048;

  public static long seed = new Random().nextInt();
  public static final Random random = new Random(seed);

  private static final int MIN_CELL_COUNT = 4;

  private static Vec2 voronoiPointToVec2(VoronoiDiagram.Point point) {
    return new Vec2((int) Math.round(point.x()), (int) Math.round(point.y()));
  }

  public static World generateWorld() {
    System.out.println("seed=" + seed);
    List<Vec2> cellCenters =
        UniformlyDistributedPoints.generateUniformlyDistributedPoints(100, 30, dimension, random);

    List<VoronoiDiagram.Cell> cells =
        VoronoiDiagram.generateVoronoiDiagram(
            cellCenters.stream().map(vec2 -> new VoronoiDiagram.Point(vec2.x(), vec2.y())).toList(),
            dimension,
            dimension);

    Collection<Biome> biomes = createInitialBiomes(cells);

    growBiomesToMinSize(biomes);

    return new World(biomes.stream().toList());
  }

  private static void growBiomesToMinSize(Collection<Biome> biomes) {
    var smallBiomeIterator = biomes.iterator();
    while (smallBiomeIterator.hasNext()) {
      Biome biome = smallBiomeIterator.next();

      if (biome.cells().size() >= MIN_CELL_COUNT) continue;

      Biome otherBiome = biome.neighbors().get(random.nextInt(biome.neighbors().size()));

      otherBiome.cells().addAll(biome.cells());

      mergePolygons(otherBiome.polygon(), biome.polygon());

      for (var neighbor : biome.neighbors()) {
        neighbor.neighbors().remove(biome);

        if (neighbor != otherBiome) {
          if (!neighbor.neighbors().contains(otherBiome)) neighbor.neighbors().add(otherBiome);

          if (!otherBiome.neighbors().contains(neighbor)) otherBiome.neighbors().add(neighbor);
        }
      }

      smallBiomeIterator.remove();
    }
  }

  /** Merge {otherPolygon} into {polygon}. Polygons must share intersection points. */
  private static void mergePolygons(List<Vec2> polygon, List<Vec2> otherPolygon) {
    if (polygon.isEmpty() || otherPolygon.isEmpty()) return;

    int matched = -1;
    int matchedOther = -1;
    for (int i = 0; i < polygon.size() && matchedOther == -1; i++) {
      matched = i;
      matchedOther = otherPolygon.indexOf(polygon.get(i));
    }

    int matchedFirst = matched;

    if (matchedOther == -1)
      throw new IllegalArgumentException(
          "Polygon " + otherPolygon + " has no shared points with " + polygon);

    if (matched == 0) {
      for (int i = 1;
          i < polygon.size()
              && polygon
                  .get(polygon.size() - i)
                  .equals(otherPolygon.get((matchedOther + i) % otherPolygon.size()));
          i++) {
        matched = polygon.size() - i;
        matchedOther = (matchedOther + i) % otherPolygon.size();
      }
    }

    int matchLen = matchedFirst != matched ? polygon.size() - matched + 1 : 1;
    while (polygon
        .get((matched + matchLen) % polygon.size())
        .equals(
            otherPolygon.get(
                (matchedOther - matchLen + otherPolygon.size()) % otherPolygon.size()))) {
      matchLen += 1;
    }

    int oldPolygonSize = polygon.size();
    for (int i = 0; i < matchLen - 2; i++) {
      polygon.remove((matched + 1) % oldPolygonSize);
    }

    int indexOffset = (matched + 1) % oldPolygonSize;
    for (int i = 0; i < otherPolygon.size() - matchLen; i++) {
      polygon.add(indexOffset + i, otherPolygon.get((matchedOther + i + 1) % otherPolygon.size()));
    }
  }

  private static Collection<Biome> createInitialBiomes(List<VoronoiDiagram.Cell> cells) {
    Map<Vec2, Biome> centerToBiome =
        cells.stream()
            .collect(
                Collectors.toMap(
                    cell -> voronoiPointToVec2(cell.site()),
                    cell -> {
                      BiomeType type =
                          BiomeType.values()[random.nextInt(BiomeType.values().length)];
                      List<Vec2> centers = new ArrayList<>();
                      centers.add(voronoiPointToVec2(cell.site()));
                      List<Vec2> polygon = new ArrayList<>();
                      cell.polygon()
                          .forEach(
                              p -> {
                                if (!polygon.contains(voronoiPointToVec2(p)))
                                  polygon.add(voronoiPointToVec2(p));
                              });
                      List<Biome> neighbors = new ArrayList<>();
                      return new Biome(type, centers, polygon, neighbors);
                    }));

    for (VoronoiDiagram.Cell cell : cells) {
      List<Biome> neighbors = centerToBiome.get(voronoiPointToVec2(cell.site())).neighbors();
      for (var neighborCenter : cell.neighbors()) {
        neighbors.add(centerToBiome.get(voronoiPointToVec2(neighborCenter)));
      }
    }

    return centerToBiome.values();
  }
}
