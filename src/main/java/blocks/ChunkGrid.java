package blocks;

import com.jme3.asset.AssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.simsilica.mathd.Vec3i;
import lombok.Getter;
import lombok.NonNull;

import java.util.function.Function;

public class ChunkGrid {
  private final int chunkWidth;
  private final int chunkHeight;
  private final int chunkDepth;

  private final int gridWidth;
  private final int gridHeight;
  private final int gridDepth;
  private final Vec3i chunkSize;
  private Chunk[][][] grid;
  private final Function<Vec3i, Block[][][]> createChunkBlocks;
  private final AssetManager assetManager;

  private int gridOffsetX;
  private int gridOffsetZ;
  private final Vec3i centerGridLocation;
  private Vector3f centerWorldLocation;
  @Getter private final Node node;

  public ChunkGrid(
      int chunkWidth,
      int chunkHeight,
      int chunkDepth,
      int gridWidth,
      int gridHeight,
      int gridDepth,
      @NonNull Vector3f centerWorldLocation,
      @NonNull AssetManager assetManager,
      @NonNull Function<Vec3i, Block[][][]> createChunkBlocks) {
    this.chunkWidth = chunkWidth;
    this.chunkHeight = chunkHeight;
    this.chunkDepth = chunkDepth;
    this.chunkSize = new Vec3i(chunkWidth, chunkHeight, chunkDepth);
    this.gridWidth = gridWidth;
    this.gridHeight = gridHeight;
    this.gridDepth = gridDepth;
    this.createChunkBlocks = createChunkBlocks;
    this.centerWorldLocation = centerWorldLocation;
    this.assetManager = assetManager;

    centerGridLocation = calculateCenterGridLocation(centerWorldLocation);
    node = new Node();

    initGrid();
  }

  private void initGrid() {
    grid = new Chunk[gridWidth][gridHeight][gridDepth];
    gridOffsetX = 0;
    gridOffsetZ = 0;

    Vec3i lowerLeftLocation = calculateLowerLeftGridLocation(centerWorldLocation);

    for (int x = 0; x < gridWidth; x++) {
      for (int y = 0; y < gridHeight; y++) {
        for (int z = 0; z < gridDepth; z++) {
          Vec3i location = lowerLeftLocation.add(x, y, z);
          Chunk chunk =
              new Chunk(location, chunkSize, createChunkBlocks.apply(location), assetManager);
          grid[x][y][z] = chunk;
          node.attachChild(chunk.getNode());
        }
      }
    }
  }

  private Vec3i calculateCenterGridLocation(Vector3f camLocation) {
    // round because both (int)-0.9f and (int)+0.9f result in 0, effectively spanning 2 ints
    return new Vec3i(
        Math.round(camLocation.x / chunkWidth), 0, Math.round((camLocation.z / chunkDepth)));
  }

  private Vec3i calculateLowerLeftGridLocation(Vector3f camLocation) {
    return new Vec3i(
        Math.round(camLocation.x / chunkWidth - gridWidth / 2f),
        0,
        Math.round(camLocation.z / chunkDepth - gridDepth / 2f));
  }

  public void centerAroundWorldLocation(Vector3f newCenterWorldLocation) {
    Vec3i newCenterGridLocation = calculateCenterGridLocation(newCenterWorldLocation);
    Vec3i newLowerLeftLocation = calculateLowerLeftGridLocation(newCenterWorldLocation);
    Vec3i oldLowerLeftLocation = calculateLowerLeftGridLocation(centerWorldLocation);
    centerWorldLocation = newCenterWorldLocation;

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

  private void stepTowardsNewCenterGridLocationX(
      Vec3i newPlayerGridLocation, Vec3i newLowerLeftLocation, Vec3i oldLowerLeftLocation) {
    boolean isPlus = newPlayerGridLocation.x > centerGridLocation.x;
    int gridX = isPlus ? gridOffsetX : (gridOffsetX + gridWidth - 1) % gridWidth;
    int locationX = isPlus ? newLowerLeftLocation.x + gridWidth - 1 : newLowerLeftLocation.x;

    for (int y = 0; y < gridHeight; y++) {
      for (int z = 0; z < gridDepth; z++) {
        int gridZ = (gridOffsetZ + z) % gridDepth;

        node.detachChild(grid[gridX][y][gridZ].getNode());

        Vec3i location = new Vec3i(locationX, y, oldLowerLeftLocation.z + z);

        Chunk chunk =
            new Chunk(location, chunkSize, createChunkBlocks.apply(location), assetManager);
        grid[gridX][y][gridZ] = chunk;
        node.attachChild(chunk.getNode());
      }
    }

    int gridOffsetDelta = isPlus ? 1 : gridWidth - 1;
    gridOffsetX = (gridOffsetX + gridOffsetDelta) % gridWidth;

    centerGridLocation.x += isPlus ? 1 : -1;

    newLowerLeftLocation.x += isPlus ? 1 : -1;
    oldLowerLeftLocation.x += isPlus ? 1 : -1;
  }

  private void stepTowardsNewCenterGridLocationZ(
      Vec3i newPlayerGridLocation, Vec3i newLowerLeftLocation, Vec3i oldLowerLeftLocation) {
    boolean isPlus = newPlayerGridLocation.z > centerGridLocation.z;
    int gridZ = isPlus ? gridOffsetZ : (gridOffsetZ + gridDepth - 1) % gridDepth;
    int locationZ = isPlus ? newLowerLeftLocation.z + gridDepth - 1 : newLowerLeftLocation.z;

    for (int x = 0; x < gridWidth; x++) {
      int gridX = (gridOffsetX + x) % gridWidth;

      for (int y = 0; y < gridHeight; y++) {
        node.detachChild(grid[gridX][y][gridZ].getNode());

        Vec3i location = new Vec3i(oldLowerLeftLocation.x + x, y, locationZ);
        Chunk chunk =
            new Chunk(location, chunkSize, createChunkBlocks.apply(location), assetManager);
        grid[gridX][y][gridZ] = chunk;
        node.attachChild(chunk.getNode());
      }
    }

    int gridOffsetDelta = isPlus ? 1 : gridDepth - 1;
    gridOffsetZ = (gridOffsetZ + gridOffsetDelta) % gridDepth;

    centerGridLocation.z += isPlus ? 1 : -1;
  }
}
