package blocks;

import com.jme3.math.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PlayerSystem {
  private final List<PlayerEntity> entities;
  private final ChunkGrid chunkGrid;

  public PlayerSystem(ChunkGrid chunkGrid) {
    this.entities = new ArrayList<>();
    this.chunkGrid = chunkGrid;
  }

  public void add(PlayerEntity entities) {
    this.entities.add(entities);
  }

  public void update(float tpf) {
    for (PlayerEntity entity : entities) {
      entity.spatial.setLocalRotation(entity.rotation);

      Vector3f stepDistance = entity.rotation.mult(entity.direction).mult(entity.velocity * tpf);

      if (entity.isSpectating) {
        entity.location.addLocal(stepDistance);
      } else {
        entity.location.addLocal(stepDistance.setY(0));

        if (getAdjacentBlock(entity, true, 1).isPresent()) { // height == 2
          alignLocationWithWorldCoordinates(entity);
        } else {
          if (getAdjacentBlock(entity, true, 0).isPresent()) {
            if (getAdjacentBlock(entity, true, 2).isEmpty()) { // height == 2
              // we already checked for yOffset+1 in if() above
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
        }
      }

      entity.spatial.setLocalTranslation(entity.location);
    }
  }

  private static void alignLocationWithWorldCoordinates(PlayerEntity entity) {
    Vector3f entityFloorLocation = entity.location.add(0, -entity.size.y / 2f, 0);
    Vector3f distanceToFrontBodyEdge =
        entity.rotation.mult(entity.size.mult(new Vector3f(0.5f, 0, 0)));
    Vector3f frontLocation = entityFloorLocation.addLocal(distanceToFrontBodyEdge);

    Vector3f diff =
        new Vector3f(
            entity.direction.x * Math.abs(frontLocation.x % 1),
            0,
            entity.direction.z * Math.abs(frontLocation.z % 1));
    entity.location.subtractLocal(diff);
  }

  private Optional<Block> getAdjacentBlock(PlayerEntity entity, boolean checkInFront, int yOffset) {
    int factor = checkInFront ? 1 : -1;
    Vector3f entityFloorLocation = entity.location.add(0, yOffset - entity.size.y / 2f, 0);
    Vector3f distanceToBodyEdge = // front or back
        entity.rotation.mult(entity.size.mult(new Vector3f(factor / 2f, 0, 0)));
    Vector3f blockLocation = entityFloorLocation.addLocal(distanceToBodyEdge);

    return chunkGrid.getBlock(
        (int) Math.floor(blockLocation.x),
        (int) Math.floor(blockLocation.y),
        (int) Math.floor(blockLocation.z));
  }
}
