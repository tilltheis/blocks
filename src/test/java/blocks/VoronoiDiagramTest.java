package blocks;

import blocks.VoronoiDiagram.Cell;
import blocks.VoronoiDiagram.Point;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.generator.Distinct;
import com.pholser.junit.quickcheck.generator.InRange;
import com.pholser.junit.quickcheck.generator.Size;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@RunWith(JUnitQuickcheck.class)
public class VoronoiDiagramTest {
  @Property
  public void neighborsSharePolygonPoints(
      //      @When(seed = 8386372202787277364L)
      @Size(max = 100) @Distinct List<@InRange(min = "0", max = "999") Double> xyPairs) {
    List<Point> sites = new ArrayList<>();
    for (int i = 0; i < xyPairs.size() - 1; i++) {
      sites.add(new Point(xyPairs.get(i), xyPairs.get(i + 1)));
    }

    List<Cell> cells = VoronoiDiagram.generateVoronoiDiagram(sites, 1000, 1000);

    for (Cell cell : cells) {
      for (Point neighbor : cell.neighbors()) {
        Cell neighborCell = cells.stream().filter(c -> c.site() == neighbor).findFirst().get();
        List<Point> sharedPoints =
            cell.polygon().stream().filter(neighborCell.polygon()::contains).toList();

        if (sharedPoints.isEmpty()) {
          System.out.println(
              cell.site() + " and " + neighbor + " are neighbors but share no polygon points.");
          System.out.println("cell.polygon() = " + cell.polygon());
          System.out.println("neighborCell.polygon() = " + neighborCell.polygon());
          System.out.println();
        }

        assertNotEquals(0, sharedPoints.size()); // assertFalse() will not show seed...
      }
    }
  }

  @Property(trials = 1000)
  public void closestSiteToPolygonSiteMustBeTheCellSite(
      @When(seed = 3010674215587625355L) @Size(max = 20) @Distinct
          List<@InRange(min = "0", max = "999") Double> xyPairs) {
    List<Point> sites = new ArrayList<>();
    for (int i = 0; i < xyPairs.size() - 1; i++) {
      sites.add(new Point(xyPairs.get(i), xyPairs.get(i + 1)));
    }
    System.out.println();

    List<Cell> cells = VoronoiDiagram.generateVoronoiDiagram(sites, 1000, 1000);

    for (Cell cell : cells) {
      for (Point point : cell.polygon()) {
        List<Point> closestSites = new ArrayList<>();
        double minDistance = Double.MAX_VALUE;
        for (Point site : sites) {
          double distance = distanceSquared(point, site);
          if (Math.abs(distance - minDistance) < 2000) {
            closestSites.add(site);
          } else if (distance < minDistance) {
            minDistance = distance;
            closestSites.clear();
            closestSites.add(site);
          }
        }

        if (!closestSites.contains(cell.site())) {
          System.out.println("point                = " + point);
          System.out.println("cell.site()          = " + cell.site());
          for (int i = 0; i < closestSites.size(); i++) {
            System.out.println("closestSites.get(" + i + ")) = " + closestSites.get(i));
          }
          System.out.println();

          System.out.println("minDistance                                 = " + minDistance);
          System.out.println(
              "distanceSquared(point, cell.site())         = "
                  + distanceSquared(point, cell.site()));
          for (int i = 0; i < closestSites.size(); i++) {
            System.out.println(
                "distanceSquared(point, closestSites.get("
                    + i
                    + ")) = "
                    + distanceSquared(point, closestSites.get(i)));
          }
          System.out.println();

          System.out.println(
              "Math.abs(minDistance - distanceSquared(point,cell.site())) = "
                  + Math.abs(minDistance - distanceSquared(point, cell.site())));

          System.out.println();
          for (int i = 0; i < xyPairs.size() - 1; i++) {
            System.out.println("new Point(" + xyPairs.get(i) + ", " + xyPairs.get(i + 1) + "),");
          }
        }

        assertNotEquals(false, closestSites.contains(cell.site()));
      }
    }
  }

  private double distanceSquared(Point point1, Point point2) {
    return (point2.x() - point1.x()) * (point2.x() - point1.x())
        + (point2.y() - point1.y()) * (point2.y() - point1.y());
  }

  @Test
  public void correctNeighbors() {
    List<Point> sites =
        List.of(
            new Point(2, 206),
            new Point(51, 290),
            new Point(64, 121),
            new Point(101, 19),
            new Point(113, 210),
            new Point(240, 86),
            new Point(268, 216));
    List<Cell> cells = VoronoiDiagram.generateVoronoiDiagram(sites, 300, 300);
    Map<Point, Set<Point>> actualNeighbors =
        cells.stream().collect(Collectors.toMap(Cell::site, c -> new HashSet<>(c.neighbors())));

    Map<Point, Set<Point>> expectedNeighbors =
        Map.of(
            new Point(2, 206),
            Set.of(new Point(51, 290), new Point(113, 210), new Point(64, 121)),
            new Point(51, 290),
            Set.of(new Point(2, 206), new Point(113, 210)),
            new Point(64, 121),
            Set.of(new Point(2, 206), new Point(101, 19), new Point(113, 210), new Point(240, 86)),
            new Point(101, 19),
            Set.of(new Point(64, 121), new Point(240, 86)),
            new Point(113, 210),
            Set.of(
                new Point(2, 206),
                new Point(51, 290),
                new Point(64, 121),
                new Point(240, 86),
                new Point(268, 216)),
            new Point(240, 86),
            Set.of(
                new Point(101, 19), new Point(64, 121), new Point(113, 210), new Point(268, 216)),
            new Point(268, 216),
            Set.of(new Point(113, 210), new Point(240, 86)));

    Comparator<Point> pointComparator = Comparator.comparing(Point::x).thenComparing(Point::y);
    List<Map.Entry<Point, List<Point>>> sortedExpectedNeighbors =
        expectedNeighbors.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(pointComparator))
            .map(
                e ->
                    (Map.Entry<Point, List<Point>>)
                        new AbstractMap.SimpleEntry<>(
                            e.getKey(), e.getValue().stream().sorted(pointComparator).toList()))
            .toList();
    List<Map.Entry<Point, List<Point>>> sortedActualNeighbors =
        actualNeighbors.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(pointComparator))
            .map(
                e ->
                    (Map.Entry<Point, List<Point>>)
                        new AbstractMap.SimpleEntry<>(
                            e.getKey(), e.getValue().stream().sorted(pointComparator).toList()))
            .toList();
    assertEquals(sortedExpectedNeighbors, sortedActualNeighbors);
  }

  @Test
  public void firstTwoSitesWithSameYValue() {
    List<Point> sites = List.of(new Point(100, 100), new Point(200, 100));
    List<Cell> actualCells = VoronoiDiagram.generateVoronoiDiagram(sites, 300, 300);
    List<Cell> expectedCells =
        List.of(
            new Cell(
                new Point(100, 100),
                List.of(new Point(0, 0), new Point(150, 0), new Point(150, 300), new Point(0, 300)),
                List.of(new Point(200, 100))),
            new Cell(
                new Point(200, 100),
                List.of(
                    new Point(150, 0), new Point(300, 0), new Point(300, 300), new Point(150, 300)),
                List.of(new Point(100, 100))));
    assertEquals(expectedCells, actualCells);
  }

  @Test
  public void firstThreeSitesWithCloseYValues() {
    List<Point> sites = List.of(new Point(100, 100), new Point(200, 100), new Point(250, 100.5d));
    List<Cell> actualCells = VoronoiDiagram.generateVoronoiDiagram(sites, 300, 300);
    List<Cell> expectedCells =
        List.of(
            new Cell(
                new Point(100, 100),
                List.of(new Point(0, 0), new Point(150, 0), new Point(150, 300), new Point(0, 300)),
                List.of(new Point(200, 100))),
            new Cell(
                new Point(200, 100),
                List.of(
                    new Point(150, 0),
                    new Point(226.0025, 0),
                    new Point(223.0025, 300),
                    new Point(150, 300)),
                List.of(new Point(100, 100), new Point(250, 100.5d))),
            new Cell(
                new Point(250, 100.5d),
                List.of(
                    new Point(226.0025, 0),
                    new Point(300, 0),
                    new Point(300, 300),
                    new Point(223.0025, 300)),
                List.of(new Point(200, 100))));
    assertEquals(expectedCells, actualCells);
  }

  @Test
  public void firstThreeSitesWithCloseYValuesParallelSitesFirst() {
    List<Point> sites = List.of(new Point(100, 100), new Point(200, 100), new Point(150, 99.5));
    List<Cell> actualCells = VoronoiDiagram.generateVoronoiDiagram(sites, 300, 300);
    List<Cell> expectedCells =
        List.of(
            new Cell(
                new Point(100, 100),
                List.of(
                    new Point(0, 0),
                    new Point(124.0025, 0),
                    new Point(127.0025, 300),
                    new Point(0, 300)),
                List.of(new Point(150, 99.5))),
            new Cell(
                new Point(200, 100),
                List.of(
                    new Point(175.9975, 0),
                    new Point(300, 0),
                    new Point(300, 300),
                    new Point(172.9975, 300)),
                List.of(new Point(150, 99.5))),
            new Cell(
                new Point(150, 99.5),
                List.of(
                    new Point(124.0025, 0),
                    new Point(175.9975, 0),
                    new Point(172.9975, 300),
                    new Point(127.0025, 300)),
                List.of(new Point(100, 100), new Point(200, 100))));
    assertEquals(expectedCells, actualCells);
  }

  @Test
  public void firstTwoPointsWithSameYValueAndAnotherSite() {
    List<Point> sites = List.of(new Point(100, 200), new Point(200, 200), new Point(125, 100));
    List<Cell> actualCells = VoronoiDiagram.generateVoronoiDiagram(sites, 300, 300);
    List<Cell> expectedCells =
        List.of(
            new Cell(
                new Point(100, 200),
                List.of(
                    new Point(0, 121.875),
                    new Point(125, 153.125),
                    new Point(150, 159.375),
                    new Point(150, 300),
                    new Point(0, 300)),
                List.of(new Point(125, 100), new Point(200, 200))),
            new Cell(
                new Point(200, 200),
                List.of(
                    new Point(150, 159.375),
                    new Point(300, 46.875),
                    new Point(300, 300),
                    new Point(150, 300)),
                List.of(new Point(100, 200), new Point(125, 100))),
            new Cell(
                new Point(125, 100),
                List.of(
                    new Point(0, 0),
                    new Point(300, 0),
                    new Point(300, 46.875),
                    new Point(150, 159.375),
                    new Point(125, 153.125),
                    new Point(0, 121.875)),
                List.of(new Point(100, 200), new Point(200, 200))));
    assertEquals(expectedCells, actualCells);
  }
}
