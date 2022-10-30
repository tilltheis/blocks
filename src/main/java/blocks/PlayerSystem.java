package blocks;

import com.jme3.math.Vector3f;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
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
        stepDistance.setY(0);
        entity.location.addLocal(stepDistance);

        if (collidesWithWorld(entity, 1)) { // check head
          resolveWorldCollision(entity, stepDistance, 1);
        } else {
          if (collidesWithWorld(entity, 0)) {
            if (!collidesWithWorld(entity, 2)) { // check above head
              // we already checked for yOffset+1 in if() above
              entity.location.y += 1;
            } else {
              resolveWorldCollision(entity, stepDistance, 0);
            }
          } else {
            // this is not good enough to make it fall into single block holes but that's ok for now
            if (!collidesWithWorld(entity, -1) && !collidesWithWorld(entity, -1)) {
              entity.location.y -= 1;
            }
          }
        }
      }

      entity.spatial.setLocalTranslation(entity.location);
    }
  }

  // neither elegant nor performant but simple
  private void resolveWorldCollision(PlayerEntity entity, Vector3f originalStep, int yOffset) {
    entity.location.subtractLocal(originalStep);

    int subStepCount = 10;
    Vector3f smallStepX = originalStep.mult(new Vector3f(1f / subStepCount, 0, 0));
    Vector3f smallStepZ = originalStep.mult(new Vector3f(0, 0, 1f / subStepCount));
    boolean isBlockedX = false;
    boolean isBlockedZ = false;

    for (int i = 0; i < subStepCount && (!isBlockedX || !isBlockedZ); i++) {
      if (!isBlockedZ) {
        entity.location.addLocal(smallStepZ);
        if (collidesWithWorld(entity, yOffset)) {
          entity.location.subtractLocal(smallStepZ);
          isBlockedZ = true;
        }
      }

      if (!isBlockedX) {
        entity.location.addLocal(smallStepX);
        if (collidesWithWorld(entity, yOffset)) {
          entity.location.subtractLocal(smallStepX);
          isBlockedX = true;
        }
      }
    }
  }

  // including center block
  private boolean collidesWithWorld(PlayerEntity entity, int yOffset) {
    Vector3f entityFloorLocation = entity.location.add(0, yOffset - entity.size.y / 2f, 0);

    int centerX = (int) Math.floor(entityFloorLocation.x);
    int centerY = (int) Math.floor(entityFloorLocation.y);
    int centerZ = (int) Math.floor(entityFloorLocation.z);

    // for now entity horizontal size must be <= 1
    for (int x = 0; x < 3; x += 1) {
      for (int z = 0; z < 3; z += 1) {
        Optional<Block> optionalBlock =
            chunkGrid.getBlock(centerX - 1 + x, centerY, centerZ - 1 + z);
        if (optionalBlock.isPresent()
            && collidesWithCoordinate(
                entity, entityFloorLocation, centerX - 1 + x, centerZ - 1 + z)) return true;
      }
    }

    return false;
  }

  private boolean collidesWithCoordinate(
      PlayerEntity entity, Vector3f entityFloorLocation, int blockX, int blockZ) {
    Rectangle rectangle = new Rectangle(blockX, blockZ, 1, 1);

    Vector3f frontLeft =
        entityFloorLocation.add(
            entity.rotation.mult(entity.size.mult(new Vector3f(-0.5f, 0, 0.5f))));
    Vector3f frontRight =
        entityFloorLocation.add(
            entity.rotation.mult(entity.size.mult(new Vector3f(0.5f, 0, 0.5f))));
    Vector3f backLeft =
        entityFloorLocation.add(
            entity.rotation.mult(entity.size.mult(new Vector3f(-0.5f, 0, -0.5f))));
    Vector3f backRight =
        entityFloorLocation.add(
            entity.rotation.mult(entity.size.mult(new Vector3f(0.5f, 0, -0.5f))));

    Line2D frontLine = new Line2D.Float(frontLeft.x, frontLeft.z, frontRight.x, frontRight.z);
    Line2D backLine = new Line2D.Float(backLeft.x, backLeft.z, backRight.x, backRight.z);
    Line2D leftLine = new Line2D.Float(frontLeft.x, frontLeft.z, backLeft.x, backLeft.z);
    Line2D rightLine = new Line2D.Float(frontRight.x, frontRight.z, backRight.x, backRight.z);

    return rectangle.intersectsLine(frontLine)
        || rectangle.intersectsLine(backLine)
        || rectangle.intersectsLine(leftLine)
        || rectangle.intersectsLine(rightLine);
  }
}
