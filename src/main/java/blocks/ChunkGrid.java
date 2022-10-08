package blocks;

import com.jme3.asset.AssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.simsilica.mathd.Vec3i;
import lombok.Getter;
import lombok.NonNull;

import java.lang.reflect.Array;
import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
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

  private final ExecutorService executorService;
  private final ConcurrentLinkedQueue<Map.Entry<Integer, Chunk>> updateList =
      new ConcurrentLinkedQueue<>();

  public ChunkGrid(
      @NonNull Vec3i gridSize,
      @NonNull Vec3i chunkSize,
      @NonNull Vector3f centerWorldLocation,
      ExecutorService executorService,
      @NonNull AssetManager assetManager,
      @NonNull Function<Vec3i, Block[][][]> createChunkBlocks) {
    this.gridSize = gridSize;
    this.chunkSize = chunkSize;
    this.createChunkBlocks = createChunkBlocks;
    this.centerWorldLocation = centerWorldLocation.clone();
    this.executorService = executorService;
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
          // scheduleChunkGeneration() requires a filled node list
          node.attachChild(new Node());

          Vec3i gridLocation = new Vec3i(x, y, z);
          Vec3i chunkLocation = lowerLeftLocation.add(x, y, z);
          scheduleChunkGeneration(gridLocation, chunkLocation);
        }
      }
    }
  }

  private Vec3i calculateCenterGridLocation(Vector3f camLocation) {
    // floor because both (int)-0.9f and (int)+0.9f result in 0, effectively spanning 2 ints
    return new Vec3i(
        (int) Math.floor(camLocation.x / chunkSize.x),
        0,
        (int) Math.floor((camLocation.z / chunkSize.z)));
  }

  private Vec3i calculateLowerLeftGridLocation(Vector3f camLocation) {
    return new Vec3i(
        (int) Math.floor(camLocation.x / chunkSize.x - gridSize.x / 2f),
        0,
        (int) Math.floor(camLocation.z / chunkSize.z - gridSize.z / 2f));
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

  public void update() {
    while (!updateList.isEmpty()) {
      Map.Entry<Integer, Chunk> nodeIndexWithChunk = updateList.remove();
      node.detachChildAt(nodeIndexWithChunk.getKey());
      node.attachChildAt(nodeIndexWithChunk.getValue().getNode(), nodeIndexWithChunk.getKey());
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
        Vec3i gridLocation = new Vec3i(gridX, y, gridZ);
        Vec3i chunkLocation = new Vec3i(locationX, y, oldLowerLeftLocation.z + z);
        scheduleChunkGeneration(gridLocation, chunkLocation);
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
        Vec3i gridLocation = new Vec3i(gridX, y, gridZ);
        Vec3i chunkLocation = new Vec3i(oldLowerLeftLocation.x + x, y, locationZ);
        scheduleChunkGeneration(gridLocation, chunkLocation);
      }
    }

    int gridOffsetDelta = isPlus ? 1 : gridSize.z - 1;
    gridOffsetZ = (gridOffsetZ + gridOffsetDelta) % gridSize.z;

    centerGridLocation.z += isPlus ? 1 : -1;
  }

  private void scheduleChunkGeneration(Vec3i gridLocation, Vec3i chunkLocation) {
    int nodeIndex =
        gridLocation.x * gridSize.y * gridSize.z + gridLocation.y * gridSize.z + gridLocation.z;
    node.detachChildAt(nodeIndex);
    node.attachChildAt(new Node(), nodeIndex);

    if (grid[gridLocation.x][gridLocation.y][gridLocation.z] != null)
      grid[gridLocation.x][gridLocation.y][gridLocation.z].cancel(true);

    grid[gridLocation.x][gridLocation.y][gridLocation.z] =
        executorService.submit(
            () -> {
              try {
                Chunk chunk =
                    new Chunk(
                        chunkLocation,
                        chunkSize,
                        generateChunkBlocks(chunkLocation),
                        assetManager,
                        this);
                updateList.add(new AbstractMap.SimpleEntry<>(nodeIndex, chunk));
                return chunk;
              } catch (Throwable throwable) {
                throwable.printStackTrace();
                throw throwable;
              }
            });
  }

  // maybe this is better made private and added as a function param to the Chunk constructor
  public Block[][][] generateChunkBlocks(Vec3i chunkLocation) {
    return createChunkBlocks.apply(chunkLocation);
  }
}
