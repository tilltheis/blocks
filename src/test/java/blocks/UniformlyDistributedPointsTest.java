package blocks;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.InRange;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;

import java.text.MessageFormat;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@RunWith(JUnitQuickcheck.class)
public class UniformlyDistributedPointsTest {

  @Property
  public void minDistanceIsAdheredTo(long seed, @InRange(min = "1", max = "50") int radius) {
    List<Vec2> points =
        UniformlyDistributedPoints.generateUniformlyDistributedPoints(
            radius, 13, 200, new Random(seed));

    for (int i = 0; i < points.size(); i++) {
      for (int j = 0; j < points.size(); j++) {
        if (i == j) continue;

        Vec2 point1 = points.get(i);
        Vec2 point2 = points.get(j);

        double distance = distance(point1, point2);
        if (distance < radius) {
          System.out.println(
              MessageFormat.format(
                  "distance({0}, {1}) < {2} = {3} < {2}", point1, point2, radius, distance));
        }
        assertNotEquals(true, distance < radius); // assertFalse() will not show seed...
      }
    }
  }

  private static double distance(Vec2 point1, Vec2 point2) {
    return Math.sqrt(
        (point2.x() - point1.x()) * (point2.x() - point1.x())
            + (point2.y() - point1.y()) * (point2.y() - point1.y()));
  }
}
