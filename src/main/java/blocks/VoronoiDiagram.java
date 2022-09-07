package blocks;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Generate a Voronoi diagram using Fortune's algorithm. */
public class VoronoiDiagram {
  public record Point(double x, double y) {}

  public record Cell(Point site, List<Point> polygon, List<Point> neighbors) {}

  private sealed interface Event {
    Point point();
  }

  private record SiteEncountered(Point site) implements Event {
    @Override
    public Point point() {
      return site;
    }
  }

  private record EdgesIntersected(Point intersection, double sweepLine, Arc arc) implements Event {
    @Override
    public Point point() {
      return new Point(intersection.x, sweepLine);
    }
  }

  private static class Beachline {
    // TODO use tree
    public List<BeachlineItem> items = new ArrayList<>();

    public Beachline(Arc arc) {
      items.add(arc);
    }

    public Arc arc(int index) {
      return (Arc) items.get(index);
    }

    public Edge edge(int index) {
      return (Edge) items.get(index);
    }

    public void newSite(
        Arc oldArc, Arc leftHalfArc, Edge leftEdge, Arc newArc, Edge rightEdge, Arc rightHalfArc) {
      oldArc.isAlive = false;
      int index = items.indexOf(oldArc);
      items.remove(index);
      items.addAll(index, Arrays.asList(leftHalfArc, leftEdge, newArc, rightEdge, rightHalfArc));
    }

    public void newSiteWithInfiniteEdge(Arc oldArc, Edge infiniteEdge, Arc newArc) {
      int index = items.indexOf(oldArc);
      items.addAll(index + 1, Arrays.asList(infiniteEdge, newArc));
    }

    public void newVertex(Arc oldArc, Edge newEdge) {
      int index = items.indexOf(oldArc) - 1; // -1 is edge to the left
      items.remove(index); // left edge (now complete)
      oldArc.isAlive = false;
      items.remove(index); // arc (now complete)
      items.remove(index); // right edge (now complete)
      items.add(index, newEdge);
    }

    public Arc findArcAbove(Point point) {
      for (var i = 0; i < items.size() - 2; i += 2) {
        Arc leftArc = arc(i);
        Arc rightArc = arc(i + 2);

        Point leftFocus = leftArc.focus;
        Point rightFocus = rightArc.focus;
        double y = point.y;

        double intersectionX;

        double arcDiffY = rightFocus.y - leftFocus.y;
        if (arcDiffY == 0) intersectionX = (leftFocus.x + rightFocus.x) / 2;
        else {
          double arcDiffX = leftFocus.x - rightFocus.x;
          double leftArcDistY = y - leftFocus.y;
          double rightArcDistY = y - rightFocus.y;
          double h1 = (-leftFocus.x * rightArcDistY + rightFocus.x * leftArcDistY) / arcDiffY;
          double h2 =
              Math.sqrt(leftArcDistY * rightArcDistY * (arcDiffX * arcDiffX + arcDiffY * arcDiffY))
                  / arcDiffY;
          intersectionX = h1 + h2;
        }

        if (intersectionX >= point.x) return leftArc;
      }

      return arc(items.size() - 1);
    }
  }

  private sealed interface BeachlineItem {}

  public sealed interface FullEdge {
    Point leftSite();

    Point rightSite();
  }

  public record InfiniteFullEdge(Point origin, Point direction, Point leftSite, Point rightSite)
      implements FullEdge {}

  public record FiniteFullEdge(Point origin, Point destination, Point leftSite, Point rightSite)
      implements FullEdge {}

  // no equals or hashcode because i want object identity equality
  private static final class Edge implements BeachlineItem {
    public Point origin;
    public Point direction;
    public Arc leftArc;
    public Arc rightArc;

    public Edge(Point origin, Point direction, Arc leftArc, Arc rightArc) {
      this.origin = origin;
      this.direction = direction;
      this.leftArc = leftArc;
      this.rightArc = rightArc;
    }

    @Override
    public String toString() {
      return String.format(
          "Edge[origin=%s, direction=%s, leftArc=%s, rightArc=%s]",
          origin, direction, leftArc, rightArc);
    }
  }

  // no equals or hashcode because i want object identity equality
  private static final class Arc implements BeachlineItem {
    public Point focus;

    // the object can get used past its death if it's in an edge intersection event
    public boolean isAlive = true;

    public Arc(Point focus) {
      this.focus = focus;
    }

    @Override
    public String toString() {
      return String.format("Arc[focus=%s, isAlive=%s]", focus, isAlive);
    }
  }

  private static double calculateArcY(Arc arc, double x, double sweepLine) {
    double a = 1.0f / (2.0f * (arc.focus.y - sweepLine));
    double c = (arc.focus.y + sweepLine) * 0.5f;

    double w = x - arc.focus.x;
    return a * w * w + c;
  }

  private static double magnitude(Point point) {
    return Math.sqrt(point.x * point.x + point.y * point.y);
  }

  private static Point normalize(Point point) {
    double length = magnitude(point);
    return new Point(point.x / length, point.y / length);
  }

  private static Optional<Point> calculateEdgeIntersectionPoint(Edge leftEdge, Edge rightEdge) {
    if (Double.isInfinite(leftEdge.origin.y)) {
      // must be from initial arcs that were too close to each other
      return calculateEdgeIntersectionWithInfiniteEdge(leftEdge, rightEdge);
    }
    if (Double.isInfinite(rightEdge.origin.y)) {
      // must be from initial arcs that were too close to each other
      return calculateEdgeIntersectionWithInfiniteEdge(rightEdge, leftEdge);
    }

    double dx = rightEdge.origin.x - leftEdge.origin.x;
    double dy = rightEdge.origin.y - leftEdge.origin.y;
    double determinant =
        rightEdge.direction.x * leftEdge.direction.y - rightEdge.direction.y * leftEdge.direction.x;

    if (determinant == 0) return Optional.empty();

    double u = (dy * rightEdge.direction.x - dx * rightEdge.direction.y) / determinant;
    double v = (dy * leftEdge.direction.x - dx * leftEdge.direction.y) / determinant;

    if (u < 0.0f || v < 0.0f || (u == 0.0f && v == 0.0f)) return Optional.empty();

    Point point =
        new Point(
            leftEdge.origin.x + leftEdge.direction.x * u,
            leftEdge.origin.y + leftEdge.direction.y * u);

    return Optional.of(point);
  }

  private static Optional<Point> calculateEdgeIntersectionWithInfiniteEdge(
      Edge infiniteEdge, Edge rightEdge) {
    Edge leftEdge = infiniteEdge;

    if (leftEdge.origin.y < 0)
      throw new IllegalStateException(
          "unexpected edge with negative infinite origin y " + leftEdge);

    if (leftEdge.direction.y > 0) return Optional.empty();

    if (Double.isInfinite(rightEdge.origin.y)
        || (rightEdge.origin.x < leftEdge.origin.x && rightEdge.direction.x <= 0)
        || (rightEdge.origin.x > leftEdge.origin.x && rightEdge.direction.x >= 0))
      return Optional.empty();

    double dx = leftEdge.origin.x - rightEdge.origin.x;
    double u = dx / rightEdge.direction.x;
    return Optional.of(
        new Point(
            rightEdge.origin.x + rightEdge.direction.x * u,
            rightEdge.origin.y + rightEdge.direction.y * u));
  }

  private static final Comparator<Event> eventComparator =
      // top-down, left-right
      Comparator.comparing((Event e) -> -e.point().y).thenComparing(e -> e.point().x);

  private static Comparator<Point> polygonComparator(Point center) {
    return Comparator.comparing(
        point -> Math.atan2(point.y() - center.y(), point.x() - center.x()));
  }

  private static class State {
    public int width;
    public int height;

    public PriorityQueue<Event> events;

    public Beachline beachline;

    public Map<Point, List<Point>> diagram;
    public Map<Point, List<Point>> neighbors;

    public List<FullEdge> fullEdges;

    public State(
        int width,
        int height,
        PriorityQueue<Event> events,
        Beachline beachline,
        Map<Point, List<Point>> diagram,
        Map<Point, List<Point>> neighbors) {
      this.width = width;
      this.height = height;
      this.events = events;
      this.beachline = beachline;
      this.diagram = diagram;
      this.neighbors = neighbors;

      this.fullEdges = new ArrayList<>();
    }
  }

  public static List<Cell> generateVoronoiDiagram(List<Point> sites, int width, int height) {
    if (sites.isEmpty()) return new ArrayList<>();

    if (sites.stream().anyMatch(s -> s.x() < 0 || s.x() >= width || s.y() < 0 || s.y() >= height))
      throw new IllegalArgumentException("Sites must be bounded by dimensions.");

    PriorityQueue<Event> events = new PriorityQueue<>(eventComparator);
    Map<Point, List<Point>> diagram =
        sites.stream().collect(Collectors.toMap(x -> x, x -> new ArrayList<>()));
    Map<Point, List<Point>> neighbors =
        sites.stream().collect(Collectors.toMap(x -> x, x -> new ArrayList<>()));

    events.addAll(sites.stream().map(SiteEncountered::new).toList());

    Beachline beachline = new Beachline(new Arc(events.poll().point()));

    State state = new State(width, height, events, beachline, diagram, neighbors);

    while (!events.isEmpty()) {
      switch (events.poll()) {
        case SiteEncountered siteEncountered -> handleSiteEncounteredEvent(siteEncountered, state);
        case EdgesIntersected edgesIntersected -> handleEdgesIntersectedEvent(
            edgesIntersected, state);
      }
    }

    closeOpenEdges(state);

    clip(state);

    return diagram.entrySet().stream()
        .map(
            entry ->
                new Cell(
                    entry.getKey(),
                    entry.getValue().stream()
                        .distinct()
                        .sorted(polygonComparator(entry.getKey()))
                        .toList(),
                    neighbors.get(entry.getKey()).stream().distinct().toList()))
        .toList();
  }

  private static void clip(State state) {
    state.diagram.values().forEach(List::clear);
    state.neighbors.values().forEach(List::clear);

    Point bottomLeft = new Point(0, 0);
    Point topLeft = new Point(0, state.height);
    Point topRight = new Point(state.width, state.height);
    Point bottomRight = new Point(state.width, 0);

    for (FullEdge fullEdge : state.fullEdges) {
      List<Point> leftPolygon = state.diagram.get(fullEdge.leftSite());
      List<Point> rightPolygon = state.diagram.get(fullEdge.rightSite());
      List<Point> leftNeighbors = state.neighbors.get(fullEdge.leftSite());
      List<Point> rightNeighbors = state.neighbors.get(fullEdge.rightSite());

      Consumer<Point> f =
          p -> {
            leftPolygon.add(p);
            rightPolygon.add(p);
            leftNeighbors.add(fullEdge.rightSite());
            rightNeighbors.add(fullEdge.leftSite());
          };

      switch (fullEdge) {
        case FiniteFullEdge e -> {
          calculateLineIntersection(bottomLeft, topLeft, e.origin, e.destination).ifPresent(f);
          calculateLineIntersection(topLeft, topRight, e.origin, e.destination).ifPresent(f);
          calculateLineIntersection(topRight, bottomRight, e.origin, e.destination).ifPresent(f);
          calculateLineIntersection(bottomRight, bottomLeft, e.origin, e.destination).ifPresent(f);

          if (isWithinBounds(e.origin, state)) f.accept(e.origin);
          if (isWithinBounds(e.destination, state)) f.accept(e.destination);
        }
        case InfiniteFullEdge e -> {
          if (Double.isInfinite(e.origin.y) && e.direction.y == -1d) {
            if (Math.abs(e.direction.y) != 1d) continue;

            f.accept(new Point(e.origin.x, state.height));
            f.accept(new Point(e.origin.x, 0));
          } else {
            calculateRayLineIntersection(e.origin, e.direction, bottomLeft, topLeft).ifPresent(f);
            calculateRayLineIntersection(e.origin, e.direction, topLeft, topRight).ifPresent(f);
            calculateRayLineIntersection(e.origin, e.direction, topRight, bottomRight).ifPresent(f);
            calculateRayLineIntersection(e.origin, e.direction, bottomRight, bottomLeft)
                .ifPresent(f);

            if (isWithinBounds(e.origin, state)) f.accept(e.origin);
          }
        }
      }
    }

    // this is horribly inefficient but works
    var corners = Arrays.asList(bottomLeft, topLeft, topRight, bottomRight);
    for (var corner : corners) {
      Point closestSite =
          state.diagram.keySet().stream()
              .min(Comparator.comparing(site -> distanceSquared(site, corner)))
              .get();
      state.diagram.get(closestSite).add(corner);
    }
  }

  private static Optional<Point> calculateRayLineIntersection(
      Point rayOrigin, Point rayDirection, Point lineStart, Point lineEnd) {
    double distance =
        calculateRayLineIntersectionDistance(rayOrigin, rayDirection, lineStart, lineEnd);
    if (distance < 0) return Optional.empty();
    Point normalizedRayDirection = normalize(rayDirection);
    double intersectionX = rayOrigin.x + normalizedRayDirection.x * distance;
    double intersectionY = rayOrigin.y + normalizedRayDirection.y * distance;
    return Optional.of(new Point(intersectionX, intersectionY));
  }

  private static double calculateRayLineIntersectionDistance(
      Point rayOrigin, Point rayDirection, Point lineStart, Point lineEnd) {
    Point v1 = new Point(rayOrigin.x - lineStart.x, rayOrigin.y - lineStart.y);
    Point v2 = new Point(lineEnd.x - lineStart.x, lineEnd.y - lineStart.y);
    Point v3 = new Point(-rayDirection.y, rayDirection.x);

    double dot = v2.x * v3.x + v2.y * v3.y;
    if (Math.abs(dot) < 0.000001) return -1.0d;

    double t1 = (v2.x * v1.y - v2.y * v1.x) / dot;
    double t2 = (v1.x * v3.x + v1.y * v3.y) / dot;

    if (t1 >= 0.0 && (t2 >= 0.0 && t2 <= 1.0)) return t1;

    return -1.0d;
  }

  private static Optional<Point> calculateLineIntersection(
      Point line1Start, Point line1End, Point line2Start, Point line2End) {
    double s1_x = line1End.x - line1Start.x;
    double s1_y = line1End.y - line1Start.y;
    double s2_x = line2End.x - line2Start.x;
    double s2_y = line2End.y - line2Start.y;

    double s =
        (-s1_y * (line1Start.x - line2Start.x) + s1_x * (line1Start.y - line2Start.y))
            / (-s2_x * s1_y + s1_x * s2_y);
    double t =
        (s2_x * (line1Start.y - line2Start.y) - s2_y * (line1Start.x - line2Start.x))
            / (-s2_x * s1_y + s1_x * s2_y);

    if (s >= 0 && s <= 1 && t >= 0 && t <= 1) {
      return Optional.of(new Point(line1Start.x + (t * s1_x), line1Start.y + (t * s1_y)));
    }

    return Optional.empty();
  }

  private static void closeOpenEdges(State state) {
    for (var i = 1; i < state.beachline.items.size(); i += 2) {
      Edge edge = state.beachline.edge(i);

      if (Double.isInfinite(edge.origin.y) && edge.direction.y > 0) continue;

      Arc leftArc = state.beachline.arc(i - 1);
      Arc rightArc = state.beachline.arc(i + 1);

      state.fullEdges.add(
          new InfiniteFullEdge(edge.origin, edge.direction, leftArc.focus, rightArc.focus));
    }
  }

  private static void handleSiteEncounteredEvent(SiteEncountered siteEncountered, State state) {

    // create new arc an
    Arc newArc = new Arc(siteEncountered.site);

    // find owning arc af by x coordinate
    Arc oldArc = state.beachline.findArcAbove(siteEncountered.site);

    // create new arcs afl, afr
    Arc leftHalfArc = new Arc(oldArc.focus);
    Arc rightHalfArc = new Arc(oldArc.focus);

    // create half edges el, er
    double originY = calculateArcY(oldArc, siteEncountered.site.x, siteEncountered.site.y);
    double originX =
        Double.isInfinite(originY)
            ? (siteEncountered.site.x + oldArc.focus.x) / 2.0d
            : siteEncountered.site.x;
    Point origin = new Point(originX, originY);
    Point focusDiff = new Point(newArc.focus.x - oldArc.focus.x, newArc.focus.y - oldArc.focus.y);
    Point leftDirection = normalize(new Point(focusDiff.y, -focusDiff.x));
    Edge leftEdge = new Edge(origin, leftDirection, oldArc, newArc);
    Edge rightEdge =
        new Edge(origin, new Point(-leftDirection.x, -leftDirection.y), newArc, oldArc);

    if (Double.isInfinite(originY)) {
      // no splitting because only new, vertical edge will be inserted in between
      Edge edge = leftEdge.direction.y < 0 ? leftEdge : rightEdge;
      state.beachline.newSiteWithInfiniteEdge(oldArc, edge, newArc);
      createEdgeIntersectionEventIfNecessary(oldArc, state);
    } else {
      // remove af because it was split by afl, el, an, er, afr
      state.beachline.newSite(oldArc, leftHalfArc, leftEdge, newArc, rightEdge, rightHalfArc);

      // schedule edge intersection events
      createEdgeIntersectionEventIfNecessary(leftHalfArc, state);
      createEdgeIntersectionEventIfNecessary(rightHalfArc, state);
    }
  }

  private static boolean isWithinBounds(Point point, State state) {
    return point.x >= 0 && point.x < state.width && point.y >= 0 && point.y < state.height;
  }

  private static void handleEdgesIntersectedEvent(EdgesIntersected edgesIntersected, State state) {

    if (!edgesIntersected.arc.isAlive) return;

    int arcIndex = state.beachline.items.indexOf(edgesIntersected.arc);
    Arc leftArc = state.beachline.arc(arcIndex - 2);
    Arc rightArc = state.beachline.arc(arcIndex + 2);

    // remove arc ao
    // remove intersecting half edges el, er

    // create full edges between intersection point and half edge origins
    state.diagram.get(edgesIntersected.arc.focus).add(edgesIntersected.intersection);
    state.diagram.get(leftArc.focus).add(edgesIntersected.intersection);
    state.diagram.get(rightArc.focus).add(edgesIntersected.intersection);

    Edge leftEdge = state.beachline.edge(arcIndex - 1);
    Edge rightEdge = state.beachline.edge(arcIndex + 1);

    FullEdge leftFullEdge =
        createFullEdge(leftEdge, edgesIntersected.intersection, leftArc, edgesIntersected.arc);
    FullEdge rightFullEdge =
        createFullEdge(rightEdge, edgesIntersected.intersection, edgesIntersected.arc, rightArc);
    Collections.addAll(state.fullEdges, leftFullEdge, rightFullEdge);

    // create half edge between arcs left and right of intersection/old arc
    Point newEdgeDirection =
        normalize(
            new Point(rightArc.focus.y - leftArc.focus.y, -(rightArc.focus.x - leftArc.focus.x)));

    Edge newEdge = new Edge(edgesIntersected.intersection, newEdgeDirection, leftArc, rightArc);
    state.beachline.newVertex(edgesIntersected.arc, newEdge);

    // schedule edge intersection events
    createEdgeIntersectionEventIfNecessary(leftArc, state);
    createEdgeIntersectionEventIfNecessary(rightArc, state);
  }

  private static FullEdge createFullEdge(
      Edge halfEdge, Point destination, Arc leftArc, Arc rightArc) {
    if (Double.isInfinite(halfEdge.origin.y)) {
      if (halfEdge.origin.y < 0)
        throw new IllegalStateException("unexpected infinite direction sign " + halfEdge);

      return new InfiniteFullEdge(
          destination,
          new Point(-halfEdge.direction.x, -halfEdge.direction.y),
          leftArc.focus,
          rightArc.focus);
    } else return new FiniteFullEdge(halfEdge.origin, destination, leftArc.focus, rightArc.focus);
  }

  private static void createEdgeIntersectionEventIfNecessary(Arc arc, State state) {
    int index = state.beachline.items.indexOf(arc);

    if (index == 0 || index == state.beachline.items.size() - 1) return;

    Edge leftEdge = state.beachline.edge(index - 1);
    Edge rightEdge = state.beachline.edge(index + 1);

    Optional<Point> maybeIntersection = calculateEdgeIntersectionPoint(leftEdge, rightEdge);
    Optional<Event> maybeEvent =
        maybeIntersection.map(
            intersection -> {
              Point circleCentreOffset =
                  new Point(arc.focus.x - intersection.x, arc.focus.y - intersection.y);
              double circleRadius = magnitude(circleCentreOffset);
              double circleEventY = intersection.y - circleRadius;
              return new EdgesIntersected(intersection, circleEventY, arc);
            });
    maybeEvent.ifPresent(state.events::offer);
  }

  private static double distanceSquared(Point point1, Point point2) {
    return (point2.x - point1.x) * (point2.x - point1.x)
        + (point2.y - point1.y) * (point2.y - point1.y);
  }
}
