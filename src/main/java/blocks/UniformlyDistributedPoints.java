package blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Generate uniformly distributed points using Poisson disk sampling. */
public class UniformlyDistributedPoints {

  public static List<Vec2> generateUniformlyDistributedPoints(
      int radius, int maxTries, int dimension, Random random) {
    if (radius <= 0) throw new IllegalArgumentException("Expected radius > 0 but got " + radius);
    if (maxTries <= 0)
      throw new IllegalArgumentException("Expected maxTries > 0 but got " + maxTries);
    if (dimension <= 0)
      throw new IllegalArgumentException("Expected dimension > 0 but got " + dimension);

    List<Vec2> active = new ArrayList<>();
    List<Vec2> valid = new ArrayList<>();

    double cellSize = radius / Math.sqrt(2);

    Vec2[][] grid =
        new Vec2[(int) Math.ceil(1.0d * dimension / cellSize)]
            [(int) Math.ceil(1.0d * dimension / cellSize)];

    Vec2 firstPoint = new Vec2(random.nextInt(dimension), random.nextInt(dimension));
    storeValidPoint(active, valid, grid, cellSize, firstPoint);

    while (!active.isEmpty()) {
      int i = random.nextInt(active.size());
      Vec2 pointAtI = active.get(i);

      boolean hasFoundValidPoint = false;
      for (var k = 0; k < maxTries; k++) {
        double newPointAngle = random.nextDouble(2 * Math.PI);
        int newPointDistance = random.nextInt(radius, 2 * radius);
        Vec2 newPoint =
            new Vec2(
                (int) (pointAtI.x() + newPointDistance * Math.cos(newPointAngle)),
                (int) (pointAtI.y() + newPointDistance * Math.sin(newPointAngle)));

        if (isValidPoint(radius, dimension, grid, cellSize, newPoint)) {
          storeValidPoint(active, valid, grid, cellSize, newPoint);
          hasFoundValidPoint = true;
          break;
        }
      }

      if (!hasFoundValidPoint) active.remove(i);
    }

    return valid;
  }

  private static void storeValidPoint(
      List<Vec2> active, List<Vec2> valid, Vec2[][] grid, double cellSize, Vec2 newPoint) {
    active.add(newPoint);
    valid.add(newPoint);
    grid[(int) (newPoint.y() / cellSize)][(int) (newPoint.x() / cellSize)] = newPoint;
  }

  private static boolean isValidPoint(
      int radius, int dim, Vec2[][] grid, double cellSize, Vec2 point) {
    if (point.x() < 0 || point.x() >= dim || point.y() < 0 || point.y() >= dim) return false;

    int minY = Math.max(0, (int) (point.y() / cellSize - 2));
    int minX = Math.max(0, (int) (point.x() / cellSize - 2));
    int maxY =
        Math.min((int) Math.ceil(1.0d * dim / cellSize) - 1, (int) (point.y() / cellSize + 2));
    int maxX =
        Math.min((int) Math.ceil(1.0d * dim / cellSize) - 1, (int) (point.x() / cellSize + 2));
    for (var y = minY; y <= maxY; y++) {
      for (var x = minX; x <= maxX; x++) {
        if (grid[y][x] != null) {
          if (distance(point, grid[y][x]) < radius) return false;
        }
      }
    }

    return true;
  }

  private static int distance(Vec2 l, Vec2 r) {
    return (int) Math.sqrt(Math.pow(r.x() - l.x(), 2) + Math.pow(r.y() - l.y(), 2));
  }
}
