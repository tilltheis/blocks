package blocks;

import com.jme3.asset.AssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.simsilica.mathd.Vec3i;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;

@Slf4j
public class ChunkGrid {

  private final Vec3i gridSize;
  private final Vec3i chunkSize;
  private FutureChunkWithNode[][][] grid;
  private final ExecutorService chunkMeshGenerationExecutorService;
  private final ExecutorService chunkBlockGenerationExecutorService;
  private final Function<Vec3i, Block[][][]> createChunkBlocks;
  private final AssetManager assetManager;

  private int gridOffsetX;
  private int gridOffsetZ;
  // in the lower left corner of the grid, relative to the grid offsets
  private final Vec3i firstGridChunkLocation;
  @Getter private final Node node;

  private final ConcurrentLinkedQueue<Map.Entry<Integer, Chunk>> updateList =
      new ConcurrentLinkedQueue<>();

  public ChunkGrid(
      @NonNull Vec3i gridSize,
      @NonNull Vec3i chunkSize,
      @NonNull Vector3f centerWorldLocation,
      ExecutorService chunkBlockGenerationExecutorService,
      ExecutorService chunkMeshGenerationExecutorService,
      @NonNull AssetManager assetManager,
      @NonNull Function<Vec3i, Block[][][]> createChunkBlocks) {
    this.gridSize = gridSize;
    this.chunkSize = chunkSize;
    this.createChunkBlocks = createChunkBlocks;
    this.chunkBlockGenerationExecutorService = chunkBlockGenerationExecutorService;
    this.chunkMeshGenerationExecutorService = chunkMeshGenerationExecutorService;
    this.assetManager = assetManager;

    firstGridChunkLocation = calculateFirstGridChunkLocation(centerWorldLocation);
    node = new Node();

    initGrid();
  }

  private void initGrid() {
    grid = new FutureChunkWithNode[gridSize.x][gridSize.y][gridSize.z];
    gridOffsetX = 0;
    gridOffsetZ = 0;

    for (int x = 0; x < gridSize.x; x++) {
      for (int y = 0; y < gridSize.y; y++) {
        for (int z = 0; z < gridSize.z; z++) {
          // scheduleChunkGeneration() requires a filled node list
          node.attachChild(new Node());

          Vec3i gridLocation = new Vec3i(x, y, z);
          Vec3i chunkLocation = firstGridChunkLocation.add(x, y, z);
          scheduleChunkGeneration(gridLocation, chunkLocation);
        }
      }
    }
  }

  private Vec3i calculateFirstGridChunkLocation(Vector3f camLocation) {
    // use floor() because both (int)-0.9f and (int)+0.9f result in 0, effectively spanning 2 ints.
    // floor() intermediate result to have fixed center that's not affected by the float subtrahend
    return new Vec3i(
        (int) Math.floor(Math.floor(camLocation.x / chunkSize.x) - gridSize.x / 2f),
        0,
        (int) Math.floor(Math.floor(camLocation.z / chunkSize.z) - gridSize.z / 2f));
  }

  public void centerAroundWorldLocation(Vector3f newCenterWorldLocation) {
    Vec3i newFirstChunkLocation = calculateFirstGridChunkLocation(newCenterWorldLocation);

    while (!newFirstChunkLocation.equals(firstGridChunkLocation)) {
      if (newFirstChunkLocation.x != firstGridChunkLocation.x) {
        stepTowardsNewCenterGridLocationX(newFirstChunkLocation.x > firstGridChunkLocation.x);
      }

      if (newFirstChunkLocation.z != firstGridChunkLocation.z) {
        stepTowardsNewCenterGridLocationZ(newFirstChunkLocation.z > firstGridChunkLocation.z);
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

  private void stepTowardsNewCenterGridLocationX(boolean isPlus) {
    int gridX = isPlus ? gridOffsetX : (gridOffsetX + gridSize.x - 1) % gridSize.x;
    int chunkX = isPlus ? firstGridChunkLocation.x + gridSize.x : firstGridChunkLocation.x - 1;

    for (int y = 0; y < gridSize.y; y++) {
      for (int z = 0; z < gridSize.z; z++) {
        int gridZ = (gridOffsetZ + z) % gridSize.z;
        Vec3i gridLocation = new Vec3i(gridX, y, gridZ);
        Vec3i chunkLocation = new Vec3i(chunkX, y, firstGridChunkLocation.z + z);
        scheduleChunkGeneration(gridLocation, chunkLocation);
      }
    }

    int gridOffsetDelta = isPlus ? 1 : gridSize.x - 1;
    gridOffsetX = (gridOffsetX + gridOffsetDelta) % gridSize.x;
    firstGridChunkLocation.x += isPlus ? 1 : -1;
  }

  private void stepTowardsNewCenterGridLocationZ(boolean isPlus) {
    int gridZ = isPlus ? gridOffsetZ : (gridOffsetZ + gridSize.z - 1) % gridSize.z;
    int chunkZ = isPlus ? firstGridChunkLocation.z + gridSize.z : firstGridChunkLocation.z - 1;

    for (int x = 0; x < gridSize.x; x++) {
      int gridX = (gridOffsetX + x) % gridSize.x;

      for (int y = 0; y < gridSize.y; y++) {
        Vec3i gridLocation = new Vec3i(gridX, y, gridZ);
        Vec3i chunkLocation = new Vec3i(firstGridChunkLocation.x + x, y, chunkZ);
        scheduleChunkGeneration(gridLocation, chunkLocation);
      }
    }

    int gridOffsetDelta = isPlus ? 1 : gridSize.z - 1;
    gridOffsetZ = (gridOffsetZ + gridOffsetDelta) % gridSize.z;
    firstGridChunkLocation.z += isPlus ? 1 : -1;
  }

  private void scheduleChunkGeneration(Vec3i gridLocation, Vec3i chunkLocation) {
    int nodeIndex =
        gridLocation.x * gridSize.y * gridSize.z + gridLocation.y * gridSize.z + gridLocation.z;
    node.detachChildAt(nodeIndex);
    node.attachChildAt(new Node(), nodeIndex);

    // there would be race condition between null check, cancelling and scheduling if this method
    // was called from different threads. but it's only ever called from the main rendering thread
    if (grid[gridLocation.x][gridLocation.y][gridLocation.z] != null)
      grid[gridLocation.x][gridLocation.y][gridLocation.z].cancel();

    grid[gridLocation.x][gridLocation.y][gridLocation.z] =
        new FutureChunkWithNode(
            chunkBlockGenerationExecutorService.submit(
                () -> {
                  try {
                    Chunk chunk =
                        new Chunk(
                            chunkLocation,
                            chunkSize,
                            generateChunkBlocks(chunkLocation),
                            assetManager,
                            this);

                    grid[gridLocation.x][gridLocation.y][gridLocation.z].futureNode =
                        chunkMeshGenerationExecutorService.submit(
                            () -> {
                              try {
                                Node node = chunk.getNode();
                                updateList.add(new AbstractMap.SimpleEntry<>(nodeIndex, chunk));
                                return node;
                              } catch (Throwable throwable) {
                                throwable.printStackTrace();
                                throw throwable;
                              }
                            });

                    return chunk;
                  } catch (Throwable throwable) {
                    throwable.printStackTrace();
                    throw throwable;
                  }
                }));
  }

  private Block[][][] generateChunkBlocks(Vec3i chunkLocation) {
    return createChunkBlocks.apply(chunkLocation);
  }

  public Block[][][] getChunkBlocks(Vec3i chunkLocation) {
    Block[][][] blocks = null;

    if (chunkLocation.x < firstGridChunkLocation.x
        || chunkLocation.x >= firstGridChunkLocation.x + gridSize.x
        || chunkLocation.y < firstGridChunkLocation.y
        || chunkLocation.y >= firstGridChunkLocation.y + gridSize.y
        || chunkLocation.z < firstGridChunkLocation.z
        || chunkLocation.z >= firstGridChunkLocation.z + gridSize.z) {
      log.debug(
          "getChunkBlocks({}): requested chunk location is out of grid bounds", chunkLocation);
    } else {
      int gridX =
          (gridOffsetX + chunkLocation.x - firstGridChunkLocation.x + gridSize.x) % gridSize.x;
      int gridZ =
          (gridOffsetZ + chunkLocation.z - firstGridChunkLocation.z + gridSize.z) % gridSize.z;

      try {
        FutureChunkWithNode futureChunkWithNode = grid[gridX][chunkLocation.y][gridZ];
        if (futureChunkWithNode == null) {
          log.warn(
              "getChunkBlocks({}): grid[{}][{}][{}] is not yet initialized",
              chunkLocation,
              gridX,
              chunkLocation.y,
              gridZ);
        } else {
          Chunk chunk = futureChunkWithNode.futureChunk.get();

          if (chunk.getLocation().equals(chunkLocation)) {
            blocks = chunk.getBlocks();
          } else {
            log.error(
                "getChunkBlocks({}): found chunk with unexpected location {} (first grid chunk location {})",
                chunkLocation,
                firstGridChunkLocation,
                chunk.getLocation());
          }
        }
      } catch (InterruptedException e) {
        log.warn(
            "getChunkBlocks({}): grid[{}][{}][{}] is interrupted",
            chunkLocation,
            gridX,
            chunkLocation.y,
            gridZ);
      } catch (ExecutionException e) {
        // was already reported in the original thread
      } catch (CancellationException e) {
        log.warn(
            "getChunkBlocks({}): grid[{}][{}][{}] is cancelled",
            chunkLocation,
            gridX,
            chunkLocation.y,
            gridZ);
      }
    }

    if (blocks == null) {
      log.debug("getChunkBlocks({}): generating chunk blocks", chunkLocation);
      blocks = generateChunkBlocks(chunkLocation);
    }

    return blocks;
  }

  private static final class FutureChunkWithNode {
    public Future<Chunk> futureChunk;
    public Future<Node> futureNode = new CompletableFuture<>();

    private FutureChunkWithNode(Future<Chunk> futureChunk) {
      this.futureChunk = futureChunk;
    }

    public void cancel() {
      futureChunk.cancel(true);
      futureNode.cancel(true);
    }
  }
}
