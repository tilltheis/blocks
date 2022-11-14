package blocks;

import com.jme3.math.ColorRGBA;
import com.simsilica.mathd.Vec3i;

import java.util.Optional;

public class ChunkBlockGenerator {
  private static final Temperature[] temperatures = Temperature.values();
  private static final Block[] dirtBlocks = createTemperaturedBlocks(BlockType.DIRT);
  private static final Block[] rockBlocks = createTemperaturedBlocks(BlockType.ROCK);
  private static final Block[] waterBlocks = createTemperaturedBlocks(BlockType.WATER);
  private static final Block[] woodBlocks = createTemperaturedBlocks(BlockType.WOOD);
  private static final Block[] leafBlocks = createTemperaturedBlocks(BlockType.LEAF);
  private static final Block[] grassBlocks = createTemperaturedBlocks(BlockType.GRASS);
  private static final boolean shouldOnlyRenderTunnels = false;
  private final Vec3i chunkSize;
  private final TerrainGenerator terrainGenerator;

  public ChunkBlockGenerator(Vec3i chunkSize, TerrainGenerator terrainGenerator) {
    this.chunkSize = chunkSize;
    this.terrainGenerator = terrainGenerator;
  }

  /**
   * @return Blocks, colored for all temperatures, indexed by temperature ordinals
   */
  private static Block[] createTemperaturedBlocks(BlockType blockType) {
    Block[] blocks = new Block[temperatures.length];
    for (Temperature temperature : temperatures) {
      ColorRGBA color =
          switch (temperature) {
            case COLD -> blockType.color.clone().interpolateLocal(ColorRGBA.White, 0.5f);
            case NORMAL -> blockType.color;
            case HOT -> blockType.color.clone().interpolateLocal(ColorRGBA.Red, 0.2f);
          };
      blocks[temperature.ordinal()] = new Block(blockType, color, blockType.color.a < 1);
    }
    return blocks;
  }

  private static Block getTerrainBlock(TerrainType terrainType) {
    if (shouldOnlyRenderTunnels)
      return switch (terrainType) {
        case MOUNTAIN, FLATLAND, OCEAN_BED, OCEAN, HILL -> null;
        case CAVE -> rockBlocks[Temperature.NORMAL.ordinal()];
        case TUNNEL -> dirtBlocks[Temperature.NORMAL.ordinal()];
        case TUNNEL_ENTRANCE -> grassBlocks[Temperature.NORMAL.ordinal()];
      };

    return switch (terrainType) {
      case MOUNTAIN -> rockBlocks[Temperature.NORMAL.ordinal()];
      case FLATLAND -> grassBlocks[Temperature.NORMAL.ordinal()];
      case OCEAN_BED -> dirtBlocks[Temperature.NORMAL.ordinal()];
      case OCEAN -> waterBlocks[Temperature.NORMAL.ordinal()];
      case HILL -> dirtBlocks[Temperature.NORMAL.ordinal()];
      case CAVE, TUNNEL, TUNNEL_ENTRANCE -> null;
    };
  }

  public Block[][][] generateBlocks(Vec3i location) {
    Block[][][] blocks = new Block[chunkSize.x][chunkSize.y][chunkSize.z];

    for (int x = 0; x < chunkSize.x; x++) {
      for (int z = 0; z < chunkSize.z; z++) {
        Terrain terrain =
            terrainGenerator.terrainAt(location.x * chunkSize.x + x, location.z * chunkSize.z + z);
        float height = terrain.height();
        int scaledHeight = (int) ((height + 1) / 2 * App.WORLD_HEIGHT);

        for (int y = 0; y < chunkSize.y && y <= scaledHeight - (location.y * chunkSize.y); y++) {
          Optional<TerrainType> subterrainType = Optional.empty();
          //              terrainGenerator.subterrainAt(
          //                  location.x * chunkSize.x + x,
          //                  location.y * chunkSize.y + y,
          //                  location.z * chunkSize.z + z,
          //                  terrain);

          Block block;

          if (subterrainType.isEmpty()) {
            if (location.y * chunkSize.y + y < scaledHeight) {
              // underground
              block = getTerrainBlock(TerrainType.HILL);
            } else {
              // surface
              block = getTerrainBlock(terrain.terrainType());
            }
          } else {
            // tunnel/cave
            block = getTerrainBlock(subterrainType.get());
          }

          blocks[x][y][z] = block;
        }

        if (terrain.terrainType() == TerrainType.OCEAN_BED) {
          int scaledLandLevelHeight =
              (int) ((TerrainGenerator.LAND_LEVEL + 1) / 2 * App.WORLD_HEIGHT);
          int y = scaledLandLevelHeight - (location.y * chunkSize.y);
          if (y >= 0 && y < chunkSize.y) blocks[x][y][z] = getTerrainBlock(TerrainType.OCEAN);
        }

        if (terrain.flora().isPresent()) {
          int y = scaledHeight - (location.y * chunkSize.y);
          switch (terrain.flora().get()) {
            case TREE -> createTreeAt(
                x,
                y,
                z,
                blocks,
                woodBlocks[terrain.temperature().ordinal()],
                leafBlocks[terrain.temperature().ordinal()]);
          }
        }
      }
    }

    // create trees that spawn outside this chunk but reach into it
    int outsideTreeRangeX = Flora.TREE.size.x / 2;
    int outsideTreeRangeZ = Flora.TREE.size.z / 2;
    for (int x = -outsideTreeRangeX; x < chunkSize.x + outsideTreeRangeX; x++) {
      for (int z = -outsideTreeRangeZ; z < chunkSize.z + outsideTreeRangeZ; z++) {
        Terrain terrain =
            terrainGenerator.terrainAt(location.x * chunkSize.x + x, location.z * chunkSize.z + z);

        if (terrain.flora().isPresent() && terrain.flora().get() == Flora.TREE) {
          int scaledHeight = (int) ((terrain.height() + 1) / 2 * App.WORLD_HEIGHT);
          int y = scaledHeight - (location.y * chunkSize.y);
          createTreeAt(
              x,
              y,
              z,
              blocks,
              woodBlocks[terrain.temperature().ordinal()],
              leafBlocks[terrain.temperature().ordinal()]);
        }

        // inside of chunk has already been scanned
        if (x >= 0 && x < chunkSize.x && z == -1) z = chunkSize.z - 1;
      }
    }

    return blocks;
  }

  private void createTreeAt(
      int x, int y, int z, Block[][][] blocks, Block woodBlock, Block leafBlock) {
    Vec3i size = Flora.TREE.size;

    if (y <= -size.y || y >= chunkSize.y) return;

    int treeStartX = x - size.x / 2;
    int treeStartY = y;
    int treeStartZ = z - size.z / 2;

    int xOffset = Math.max(0, -treeStartX);
    int yOffset = Math.max(0, -treeStartY);
    int zOffset = Math.max(0, -treeStartZ);

    int xLimit = Math.min(size.x, chunkSize.x - treeStartX);
    int yLimit = Math.min(size.y, chunkSize.y - treeStartY);
    int zLimit = Math.min(size.z, chunkSize.z - treeStartZ);

    for (int i = xOffset; i < xLimit; i++) {
      int chunkX = treeStartX + i;
      for (int j = yOffset; j < yLimit; j++) {
        int chunkY = treeStartY + j;
        for (int k = zOffset; k < zLimit; k++) {
          int chunkZ = treeStartZ + k;

          Block block = null;
          if (i == size.x / 2 && k == size.z / 2) {
            block = j == size.y - 1 ? leafBlock : woodBlock;
          } else if (j >= size.y - 2) {
            if (i > 0 && i < size.x - 1 && k > 0 && k < size.z - 1) {
              block = leafBlock;
            }
          } else if (j >= size.y - 4) {
            block = leafBlock;
          }

          if (block != null && blocks[chunkX][chunkY][chunkZ] != woodBlock) {
            blocks[chunkX][chunkY][chunkZ] = block;
          }
        }
      }
    }
  }
}
