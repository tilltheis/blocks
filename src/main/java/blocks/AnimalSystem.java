package blocks;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AnimalSystem {
  private final List<AnimalEntity> entities;
  private final ChunkGrid chunkGrid;

  public AnimalSystem(ChunkGrid chunkGrid) {
    this.entities = new ArrayList<>();
    this.chunkGrid = chunkGrid;
  }

  public void add(AnimalEntity entities) {
    this.entities.add(entities);
  }

  public void update(float tpf) {
    for (AnimalEntity entity : entities) {
      if (entity.timeLeftToMoveInDirection <= 0) {
        entity.direction =
            new Vector3f(entity.random.nextFloat(2) - 1, 0, entity.random.nextFloat(2) - 1);
        float angle = FastMath.atan2(entity.direction.z, entity.direction.x);
        entity.spatial.setLocalRotation(
            new Quaternion().fromAngleAxis(-angle, new Vector3f(0, 1, 0)));
        entity.timeLeftToMoveInDirection = entity.random.nextInt(10) + 1;
      }

      entity.timeLeftToMoveInDirection -= tpf;

      entity.location.addLocal(entity.direction.mult(tpf));

      if (getAdjacentBlock(entity, true, 0).isPresent()) {
        if (getAdjacentBlock(entity, true, 1).isEmpty()) {
          entity.location.y += 1;
        } else {
          alignLocationWithWorldCoordinates(entity);
        }
      } else {
        // this is not good enough to make it fall into single block holes but that's ok for now
        if (getAdjacentBlock(entity, true, -1).isEmpty()
            && getAdjacentBlock(entity, false, -1).isEmpty()) {
          entity.location.y -= 1;
        }
      }

      entity.spatial.setLocalTranslation(entity.location);
    }
  }

  private static void alignLocationWithWorldCoordinates(AnimalEntity entity) {
    Vector3f frontLocation = entity.location.add(entity.size.divide(2).multLocal(entity.direction));
    Vector3f diff =
        new Vector3f(
            entity.direction.x * Math.abs(frontLocation.x % 1),
            0,
            entity.direction.z * Math.abs(frontLocation.z % 1));
    entity.location.subtractLocal(diff);
  }

  private Optional<Block> getAdjacentBlock(AnimalEntity entity, boolean checkInFront, int yOffset) {
    int factor = checkInFront ? 1 : -1;
    Vector3f blockLocation =
        entity.location.add(
            entity.size.divide(2 * factor).multLocal(entity.direction).addLocal(0, yOffset, 0));
    return chunkGrid.getBlock(
        (int) Math.floor(blockLocation.x),
        (int) Math.floor(blockLocation.y),
        (int) Math.floor(blockLocation.z));
  }
}
