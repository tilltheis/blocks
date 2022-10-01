package blocks;

import com.jme3.asset.AssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.mathd.Vec3i;
import lombok.Getter;
import lombok.NonNull;

import java.lang.reflect.Array;
import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;

public class ChunkGrid {

  private final Vec3i gridSize;
  private final Vec3i chunkSize;
  private Future<Chunk>[][][] grid;
  private final Function<Vec3i, Block[][][]> createChunkBlocks;
  private final AssetManager assetManager;

  private int gridOffsetX;
  private int gridOffsetZ;
  private final Vec3i centerGridLocation;
  private Vector3f centerWorldLocation;
  @Getter private final Node node;

  private final ExecutorService executor = Executors.newFixedThreadPool(4);
  private final ConcurrentLinkedQueue<Map.Entry<Integer, Chunk>> updateList =
      new ConcurrentLinkedQueue<>();

  public ChunkGrid(
      @NonNull Vec3i gridSize,
      @NonNull Vec3i chunkSize,
      @NonNull Vector3f centerWorldLocation,
      @NonNull AssetManager assetManager,
      @NonNull Function<Vec3i, Block[][][]> createChunkBlocks) {
    this.gridSize = gridSize;
    this.chunkSize = chunkSize;
    this.createChunkBlocks = createChunkBlocks;
    this.centerWorldLocation = centerWorldLocation.clone();
    this.assetManager = assetManager;

    this.centerWorldLocation.set(1, 0f);

    centerGridLocation = calculateCenterGridLocation(centerWorldLocation);
    node = new Node();

    initGrid();
  }

  private void initGrid() {
    grid =
        (Future<Chunk>[][][]) Array.newInstance(Future.class, gridSize.x, gridSize.y, gridSize.z);
    gridOffsetX = 0;
    gridOffsetZ = 0;

    Vec3i lowerLeftLocation = calculateLowerLeftGridLocation(centerWorldLocation);

    for (int x = 0; x < gridSize.x; x++) {
      for (int y = 0; y < gridSize.y; y++) {
        for (int z = 0; z < gridSize.z; z++) {
          Vec3i location = lowerLeftLocation.add(x, y, z);
          Chunk chunk =
              new Chunk(location, chunkSize, createChunkBlocks.apply(location), assetManager);
          grid[x][y][z] = CompletableFuture.completedFuture(chunk);
          node.attachChild(chunk.getNode());
        }
      }
    }
  }

  private Vec3i calculateCenterGridLocation(Vector3f camLocation) {
    // round because both (int)-0.9f and (int)+0.9f result in 0, effectively spanning 2 ints
    return new Vec3i(
        Math.round(camLocation.x / chunkSize.x), 0, Math.round((camLocation.z / chunkSize.z)));
  }

  private Vec3i calculateLowerLeftGridLocation(Vector3f camLocation) {
    return new Vec3i(
        Math.round(camLocation.x / chunkSize.x - gridSize.x / 2f),
        0,
        Math.round(camLocation.z / chunkSize.z - gridSize.z / 2f));
  }

  public void centerAroundWorldLocation(Vector3f newCenterWorldLocation) {
    Vec3i newCenterGridLocation = calculateCenterGridLocation(newCenterWorldLocation);
    Vec3i newLowerLeftLocation = calculateLowerLeftGridLocation(newCenterWorldLocation);
    Vec3i oldLowerLeftLocation = calculateLowerLeftGridLocation(centerWorldLocation);
    centerWorldLocation = newCenterWorldLocation.clone();
    centerWorldLocation.set(1, 0f);

    if (!newCenterGridLocation.equals(centerGridLocation)) {
      // TODO the stepping functions don't properly work if multiple steps are required

      while (newCenterGridLocation.x != centerGridLocation.x) {
        stepTowardsNewCenterGridLocationX(
            newCenterGridLocation, newLowerLeftLocation, oldLowerLeftLocation);
      }

      while (newCenterGridLocation.z != centerGridLocation.z) {
        stepTowardsNewCenterGridLocationZ(
            newCenterGridLocation, newLowerLeftLocation, oldLowerLeftLocation);
      }
    }
  }

  // TODO: stick to original grid plan but increase its size to ~10k nodes => more efficient w/ same
  //   result of seeing far
  public void update() {
    Vector3f chunkGenerationDistance = gridSize.toVector3f().mult(chunkSize.toVector3f());
    int maxChunkGenerationDistance =
        (int)
            Math.max(
                Math.max(Math.max(0, chunkGenerationDistance.x), chunkGenerationDistance.y),
                chunkGenerationDistance.z);
    int squaredMaxChunkGenerationDistance = maxChunkGenerationDistance * maxChunkGenerationDistance;

    while (!updateList.isEmpty()) {
      Map.Entry<Integer, Chunk> nodeIndexWithChunk = updateList.remove();

      if (node.getChildren().size() >= 10000) {
        int index = 0;
        // only delete if child is more than the chunk generation distance away
        for (Spatial child : node.getChildren()) {
          float distanceSquared = child.getWorldTranslation().distanceSquared(centerWorldLocation);
          if (distanceSquared > squaredMaxChunkGenerationDistance) {
            break;
          }
          index += 1;
        }
        if (index < node.getChildren().size()) {
          node.detachChildAt(index);
        }
      }

      node.attachChild(nodeIndexWithChunk.getValue().getNode());
    }
  }

  private void stepTowardsNewCenterGridLocationX(
      Vec3i newPlayerGridLocation, Vec3i newLowerLeftLocation, Vec3i oldLowerLeftLocation) {
    boolean isPlus = newPlayerGridLocation.x > centerGridLocation.x;
    int gridX = isPlus ? gridOffsetX : (gridOffsetX + gridSize.x - 1) % gridSize.x;
    int locationX = isPlus ? newLowerLeftLocation.x + gridSize.x - 1 : newLowerLeftLocation.x;

    for (int y = 0; y < gridSize.y; y++) {
      for (int z = 0; z < gridSize.z; z++) {
        int gridZ = (gridOffsetZ + z) % gridSize.z;

        int nodeIndex = gridX * gridSize.y * gridSize.z + y * gridSize.z + gridZ;
        //        node.detachChildAt(nodeIndex);
        //        node.attachChildAt(new Node(), nodeIndex);

        int finalY = y;
        int finalZ = z;
        grid[gridX][y][gridZ] =
            executor.submit(
                () -> {
                  Vec3i location = new Vec3i(locationX, finalY, oldLowerLeftLocation.z + finalZ);
                  Chunk chunk =
                      new Chunk(
                          location, chunkSize, createChunkBlocks.apply(location), assetManager);
                  updateList.add(new AbstractMap.SimpleEntry<>(nodeIndex, chunk));
                  return chunk;
                });
      }
    }

    int gridOffsetDelta = isPlus ? 1 : gridSize.x - 1;
    gridOffsetX = (gridOffsetX + gridOffsetDelta) % gridSize.x;

    centerGridLocation.x += isPlus ? 1 : -1;

    newLowerLeftLocation.x += isPlus ? 1 : -1;
    oldLowerLeftLocation.x += isPlus ? 1 : -1;
  }

  private void stepTowardsNewCenterGridLocationZ(
      Vec3i newPlayerGridLocation, Vec3i newLowerLeftLocation, Vec3i oldLowerLeftLocation) {
    boolean isPlus = newPlayerGridLocation.z > centerGridLocation.z;
    int gridZ = isPlus ? gridOffsetZ : (gridOffsetZ + gridSize.z - 1) % gridSize.z;
    int locationZ = isPlus ? newLowerLeftLocation.z + gridSize.z - 1 : newLowerLeftLocation.z;

    for (int x = 0; x < gridSize.x; x++) {
      int gridX = (gridOffsetX + x) % gridSize.x;

      for (int y = 0; y < gridSize.y; y++) {
        int nodeIndex = gridX * gridSize.y * gridSize.z + y * gridSize.z + gridZ;
        //        node.detachChildAt(nodeIndex);
        //        node.attachChildAt(new Node(), nodeIndex);

        int finalX = x;
        int finalY = y;
        grid[gridX][y][gridZ] =
            executor.submit(
                () -> {
                  Vec3i location = new Vec3i(oldLowerLeftLocation.x + finalX, finalY, locationZ);
                  Chunk chunk =
                      new Chunk(
                          location, chunkSize, createChunkBlocks.apply(location), assetManager);
                  updateList.add(new AbstractMap.SimpleEntry<>(nodeIndex, chunk));
                  return chunk;
                });
      }
    }

    int gridOffsetDelta = isPlus ? 1 : gridSize.z - 1;
    gridOffsetZ = (gridOffsetZ + gridOffsetDelta) % gridSize.z;

    centerGridLocation.z += isPlus ? 1 : -1;
  }
}
