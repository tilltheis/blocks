package blocks;

import com.jme3.asset.AssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.mathd.Vec3i;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * The chunk grid contains chunks and their nodes centered around a central point. The outer layer
 * is only used for caching chunk but doesn't contain their nodes.<code>
 *
 *      grid
 *  4 - - - - -
 *  3 - N N N -
 *  2 - N N N -
 *  1 - N N N -
 *  0 - - - - -
 *    0 1 2 3 4
 *
 * </code> In the example grid above 'N' denotes a stored chunk with node, while '-' denotes an
 * empty cell.
 *
 * <p>The grid contents don't get rearranged completely when its center changes. Only its origin
 * (gridOffsetX and gridOffsetY) shifts and chunks no longer needed are replaced by the new ones.
 * <code>
 *
 *     grid
 * 4 - - N N N
 * 3 - c c c -
 * 2 - c c c c
 * 1 - c N N N
 * 0 - c N N N
 *   0 1 2 3 4
 *
 * </code> In the example grid above the origin has shifted from (0, 0) to (2, 3). 'c' denotes a
 * cached chunk.
 */
@Slf4j
public class ChunkGrid {

  // x and y sizes are 2 bigger than required - the first and last rows/cols only cache
  // pre-calculated chunk blocks that have been requested using getChunkBlocks().
  // those cells are not represented by any nodes
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
    this.gridSize = gridSize.add(2, 2, 2);
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
          if (gridLocation.x == 0
              || gridLocation.x == gridSize.x - 1
              || gridLocation.y == 0
              || gridLocation.y == gridSize.y - 1
              || gridLocation.z == 0
              || gridLocation.z == gridSize.z - 1) {
            clearNodeAt(gridLocation, "empty");
          } else {
            Vec3i chunkLocation = firstGridChunkLocation.add(x, y, z);
            scheduleChunkGeneration(gridLocation, chunkLocation);
          }
        }
      }
    }

    //    log.debug("initially\n" + debugView());
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
      //      log.debug("before\n" + debugView());

      if (newFirstChunkLocation.x != firstGridChunkLocation.x) {
        stepTowardsNewCenterGridLocationX(newFirstChunkLocation.x > firstGridChunkLocation.x);
      }

      if (newFirstChunkLocation.z != firstGridChunkLocation.z) {
        stepTowardsNewCenterGridLocationZ(newFirstChunkLocation.z > firstGridChunkLocation.z);
      }

      //      log.debug("after\n" + debugView());
    }
  }

  public void update() {
    while (!updateList.isEmpty()) {
      Map.Entry<Integer, Chunk> nodeIndexWithChunk = updateList.remove();
      node.detachChildAt(nodeIndexWithChunk.getKey());
      node.attachChildAt(nodeIndexWithChunk.getValue().getNode(), nodeIndexWithChunk.getKey());
    }
  }

  private FutureChunkWithNode gridChunkAt(int x, int y, int z) {
    return grid[gridIndexX(x)][gridIndexY(y)][gridIndexZ(z)];
  }

  private int gridIndexX(int x) {
    return (x + gridSize.x) % gridSize.x;
  }

  private int gridIndexY(int y) {
    return (y + gridSize.y) % gridSize.y;
  }

  private int gridIndexZ(int z) {
    return (z + gridSize.z) % gridSize.z;
  }

  private void stepTowardsNewCenterGridLocationX(boolean isPlus) {
    int gridX = gridIndexX(isPlus ? gridOffsetX - 1 : gridOffsetX);
    int chunkX = isPlus ? firstGridChunkLocation.x + gridSize.x - 1 : firstGridChunkLocation.x;
    int clearX = gridIndexX(isPlus ? gridOffsetX + 1 : gridOffsetX - 2);

    for (int y = 0; y < gridSize.y; y++) {
      for (int z = 0; z < gridSize.z; z++) {
        int gridZ = gridIndexZ(gridOffsetZ + z);
        Vec3i gridLocation = new Vec3i(gridX, y, gridZ);
        Vec3i chunkLocation = new Vec3i(chunkX, y, firstGridChunkLocation.z + z);

        clearNodeAt(gridLocation.clone().set(0, clearX), "empty");

        if (y > 0 && y < gridSize.y - 1 && z > 0 && z < gridSize.z - 1) {
          scheduleChunkGeneration(gridLocation, chunkLocation);
        }
      }
    }

    gridOffsetX = gridIndexX(isPlus ? gridOffsetX + 1 : gridOffsetX - 1);
    firstGridChunkLocation.x += isPlus ? 1 : -1;
  }

  private void stepTowardsNewCenterGridLocationZ(boolean isPlus) {
    int gridZ = gridIndexZ(isPlus ? gridOffsetZ - 1 : gridOffsetZ);
    int chunkZ = isPlus ? firstGridChunkLocation.z + gridSize.z - 1 : firstGridChunkLocation.z;
    int clearZ = gridIndexZ(isPlus ? gridOffsetZ + 1 : gridOffsetZ - 2);

    for (int x = 0; x < gridSize.x; x++) {
      int gridX = gridIndexX(gridOffsetX + x);

      for (int y = 0; y < gridSize.y; y++) {
        Vec3i gridLocation = new Vec3i(gridX, y, gridZ);
        Vec3i chunkLocation = new Vec3i(firstGridChunkLocation.x + x, y, chunkZ);

        clearNodeAt(gridLocation.clone().set(2, clearZ), "empty");

        if (x > 0 && x < gridSize.x - 1 && y > 0 && y < gridSize.y - 1) {
          scheduleChunkGeneration(gridLocation, chunkLocation);
        }
      }
    }

    gridOffsetZ = gridIndexZ(isPlus ? gridOffsetZ + 1 : gridOffsetZ - 1);
    firstGridChunkLocation.z += isPlus ? 1 : -1;
  }

  private void clearNodeAt(Vec3i gridLocation, String newNodeName) {
    FutureChunkWithNode cell = grid[gridLocation.x][gridLocation.y][gridLocation.z];
    if (cell != null) {
      cell.cancel();
      cell.futureNode = null;
    }

    int nodeIndex =
        gridLocation.x * gridSize.y * gridSize.z + gridLocation.y * gridSize.z + gridLocation.z;
    node.detachChildAt(nodeIndex);
    node.attachChildAt(new Node(newNodeName), nodeIndex);
  }

  private void scheduleChunkGeneration(Vec3i gridLocation, Vec3i chunkLocation) {
    clearNodeAt(gridLocation, "loading");

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
                                int nodeIndex =
                                    gridLocation.x * gridSize.y * gridSize.z
                                        + gridLocation.y * gridSize.z
                                        + gridLocation.z;
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
      log.error(
          "getChunkBlocks({}): requested chunk location is out of grid bounds", chunkLocation);
    } else {
      int gridX = gridIndexX(gridOffsetX + chunkLocation.x - firstGridChunkLocation.x);
      int gridZ = gridIndexZ(gridOffsetZ + chunkLocation.z - firstGridChunkLocation.z);

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
      if (futureNode != null) futureNode.cancel(true);
    }
  }

  private String debugView() {
    StringBuilder sb = new StringBuilder();

    String gridTitle = "grid";
    String gridTitlePadding = " ".repeat(gridSize.x + 1 - gridTitle.length() / 2);
    sb.append(gridTitlePadding).append(gridTitle).append(gridTitlePadding);
    sb.append("    ");

    String nodeTitle = "node";
    String nodeTitlePadding = " ".repeat(gridSize.x + 1 - nodeTitle.length() / 2);
    sb.append(nodeTitlePadding).append(nodeTitle).append(nodeTitlePadding);

    sb.append('\n');

    int y = 1;

    for (int z = gridSize.z - 1; z >= 0; z--) {
      // grid
      sb.append(z).append(' ');
      for (int x = 0; x < gridSize.x; x++) {
        FutureChunkWithNode futureChunkWithNode = gridChunkAt(x, y, z);
        sb.append(
                futureChunkWithNode != null
                    ? (futureChunkWithNode.futureNode != null ? 'N' : 'c')
                    : '-')
            .append(' ');
      }

      sb.append("    ");

      // node
      sb.append(z).append(' ');
      for (int x = 0; x < gridSize.x; x++) {
        int nodeIndex = x * gridSize.y * gridSize.z + y * gridSize.z + z;
        Spatial node = this.node.getChild(nodeIndex);
        sb.append(
                "empty".equals(node.getName()) ? '-' : "loading".equals(node.getName()) ? 'L' : 'N')
            .append(' ');
      }

      sb.append('\n');
    }

    sb.append("  ");
    for (int x = 0; x < gridSize.x; x++) {
      sb.append(x).append(" ");
    }

    sb.append("    ");
    sb.append("  ");
    for (int x = 0; x < gridSize.x; x++) {
      sb.append(x).append(" ");
    }

    return sb.toString();
  }
}
