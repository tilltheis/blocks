package blocks;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.mathd.Vec3i;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Function;

@Slf4j
public class ChunkGrid {

  // x and y sizes are 2 bigger than required - the first and last rows/cols only cache
  // pre-calculated chunk blocks that have been requested using getChunkBlocks().
  // those cells are not represented by any nodes
  private final Vec3i gridSize;
  private final Vec3i chunkSize;
  private final ExecutorService chunkMeshGenerationExecutorService;
  private final ExecutorService chunkBlockGenerationExecutorService;
  private final Function<Vec3i, Block[][][]> createChunkBlocks;
  private final BlockMaterial blockMaterial;

  private int gridOffsetX;
  private int gridOffsetZ;
  // in the lower left corner of the grid, relative to the grid offsets
  private final Vec3i firstGridChunkLocation;
  @Getter private final Node node;

  private final ConcurrentLinkedQueue<NodeIndexWithChunk> updateList =
      new ConcurrentLinkedQueue<>();

  LoadingCache<Vec3i, Chunk> cachedChunks;

  public ChunkGrid(
      @NonNull Vec3i gridSize,
      @NonNull Vec3i chunkSize,
      @NonNull Vector3f centerWorldLocation,
      ExecutorService chunkBlockGenerationExecutorService,
      ExecutorService chunkMeshGenerationExecutorService,
      @NonNull BlockMaterial blockMaterial,
      @NonNull Function<Vec3i, Block[][][]> createChunkBlocks) {
    this.gridSize = gridSize;
    this.chunkSize = chunkSize;
    this.createChunkBlocks = createChunkBlocks;
    this.chunkBlockGenerationExecutorService = chunkBlockGenerationExecutorService;
    this.chunkMeshGenerationExecutorService = chunkMeshGenerationExecutorService;
    this.blockMaterial = blockMaterial;

    firstGridChunkLocation = calculateFirstGridChunkLocation(centerWorldLocation);
    node = new Node();

    int chunkCacheSize = (gridSize.x + 2) * this.gridSize.y * (this.gridSize.z + 2);
    cachedChunks =
        Caffeine.newBuilder()
            .maximumSize(chunkCacheSize)
            .initialCapacity(chunkCacheSize)
            .executor(chunkBlockGenerationExecutorService)
            .build(
                chunkLocation ->
                    new Chunk(
                        chunkLocation,
                        chunkSize,
                        generateChunkBlocks(chunkLocation),
                        blockMaterial,
                        this));

    initGrid();
  }

  private void initGrid() {
    gridOffsetX = 0;
    gridOffsetZ = 0;

    for (int x = 0; x < gridSize.x; x++) {
      for (int y = 0; y < gridSize.y; y++) {
        for (int z = 0; z < gridSize.z; z++) {
          // scheduleChunkGeneration() requires a filled node list
          node.attachChild(new Node("empty"));

          Vec3i gridLocation = new Vec3i(x, y, z);
          Vec3i chunkLocation = firstGridChunkLocation.add(x, y, z);
          scheduleChunkGeneration(gridLocation, chunkLocation);
        }
      }
    }

    if (log.isDebugEnabled()) log.debug("initially\n" + debugView());
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
      if (log.isDebugEnabled()) log.debug("before\n" + debugView());

      if (newFirstChunkLocation.x != firstGridChunkLocation.x) {
        stepTowardsNewCenterGridLocationX(newFirstChunkLocation.x > firstGridChunkLocation.x);
      }

      if (newFirstChunkLocation.z != firstGridChunkLocation.z) {
        stepTowardsNewCenterGridLocationZ(newFirstChunkLocation.z > firstGridChunkLocation.z);
      }

      if (log.isDebugEnabled()) log.debug("after\n" + debugView());
    }
  }

  long startedAppAt = 0;
  long totalUpdateTime = 0;

  public void update() {
    long startedUpdateAt = System.currentTimeMillis();
    if (startedAppAt == 0) startedAppAt = startedUpdateAt;
    if (startedUpdateAt >= startedAppAt + 10000) {
      log.info("During the last 10s {}ms were spent updating", totalUpdateTime);
      startedAppAt = startedUpdateAt;
      totalUpdateTime = 0;
    }

    for (int i = updateList.size(); i > 0; i--) {
      NodeIndexWithChunk nodeIndexWithChunk = updateList.remove();
      node.detachChildAt(nodeIndexWithChunk.nodeIndex);
      node.attachChildAt(nodeIndexWithChunk.chunk.getNode(), nodeIndexWithChunk.nodeIndex);
    }

    totalUpdateTime += System.currentTimeMillis() - startedUpdateAt;
  }

  private int nodeIndexForGridLocation(Vec3i gridLocation) {
    return gridLocation.x * gridSize.y * gridSize.z + gridLocation.y * gridSize.z + gridLocation.z;
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

    for (int y = 0; y < gridSize.y; y++) {
      for (int z = 0; z < gridSize.z; z++) {
        int gridZ = gridIndexZ(gridOffsetZ + z);
        Vec3i gridLocation = new Vec3i(gridX, y, gridZ);
        Vec3i chunkLocation = new Vec3i(chunkX, y, firstGridChunkLocation.z + z);
        scheduleChunkGeneration(gridLocation, chunkLocation);
      }
    }

    gridOffsetX = gridIndexX(isPlus ? gridOffsetX + 1 : gridOffsetX - 1);
    firstGridChunkLocation.x += isPlus ? 1 : -1;
  }

  private void stepTowardsNewCenterGridLocationZ(boolean isPlus) {
    int gridZ = gridIndexZ(isPlus ? gridOffsetZ - 1 : gridOffsetZ);
    int chunkZ = isPlus ? firstGridChunkLocation.z + gridSize.z - 1 : firstGridChunkLocation.z;

    for (int x = 0; x < gridSize.x; x++) {
      int gridX = gridIndexX(gridOffsetX + x);

      for (int y = 0; y < gridSize.y; y++) {
        Vec3i gridLocation = new Vec3i(gridX, y, gridZ);
        Vec3i chunkLocation = new Vec3i(firstGridChunkLocation.x + x, y, chunkZ);
        scheduleChunkGeneration(gridLocation, chunkLocation);
      }
    }

    gridOffsetZ = gridIndexZ(isPlus ? gridOffsetZ + 1 : gridOffsetZ - 1);
    firstGridChunkLocation.z += isPlus ? 1 : -1;
  }

  private void scheduleChunkGeneration(Vec3i gridLocation, Vec3i chunkLocation) {
    chunkMeshGenerationExecutorService.submit(
        () -> {
          Chunk chunk = cachedChunks.get(chunkLocation);
          chunk.getNode();
          int nodeIndex = nodeIndexForGridLocation(gridLocation);
          updateList.add(new NodeIndexWithChunk(nodeIndex, chunk));
        });
  }

  private Block[][][] generateChunkBlocks(Vec3i chunkLocation) {
    return createChunkBlocks.apply(chunkLocation);
  }

  private Vec3i gridLocationForChunkLocation(Vec3i chunkLocation) {
    return new Vec3i(
        gridIndexX(gridOffsetX + chunkLocation.x - firstGridChunkLocation.x),
        chunkLocation.y,
        gridIndexZ(gridOffsetZ + chunkLocation.z - firstGridChunkLocation.z));
  }

  public Chunk getChunk(Vec3i chunkLocation) {
    return cachedChunks.get(chunkLocation);
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

    int y = 0;

    for (int z = gridSize.z - 1; z >= 0; z--) {
      // grid
      sb.append(z).append(' ');
      for (int x = 0; x < gridSize.x; x++) {
        Chunk chunk = cachedChunks.get(new Vec3i(x, y, z));
        sb.append(chunk.isNodeCalculationDone() ? 'N' : 'c').append(' ');
      }

      sb.append("    ");

      // node
      sb.append(z).append(' ');
      for (int x = 0; x < gridSize.x; x++) {
        int nodeIndex = x * gridSize.y * gridSize.z + y * gridSize.z + z;
        Spatial node = this.node.getChild(nodeIndex);
        sb.append("empty".equals(node.getName()) ? '-' : 'N').append(' ');
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

  public Optional<Block> getBlock(int x, int y, int z) {
    Chunk chunk =
        getChunk(
            new Vec3i(
                (int) Math.floor((float) x / chunkSize.x),
                (int) Math.floor((float) y / chunkSize.y),
                (int) Math.floor((float) z / chunkSize.z)));
    return chunk.getBlock(
        (x % chunkSize.x + chunkSize.x) % chunkSize.x,
        (y % chunkSize.y + chunkSize.y) % chunkSize.y,
        (z % chunkSize.z + chunkSize.z) % chunkSize.z);
  }

  private record NodeIndexWithChunk(int nodeIndex, Chunk chunk) {}
}
