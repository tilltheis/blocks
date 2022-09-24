package blocks;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.simsilica.mathd.Vec3i;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.util.Optional;

// TODO optimize https://0fps.net/2012/01/14/an-analysis-of-minecraft-like-engines/
@EqualsAndHashCode
@ToString(onlyExplicitlyIncluded = true)
public class Chunk {
  @ToString.Include @Getter private final Vec3i location;
  @ToString.Include @Getter private final Vec3i size;
  private final Block[][][] blocks;

  @Getter private final Node node;

  public Chunk(
      @NonNull Vec3i location,
      @NonNull Vec3i size,
      @NonNull AssetManager assetManager,
      @NonNull BlockAtFunction blockAt) {
    if (size.x < 1 || size.y < 1 || size.z < 1)
      throw new IllegalArgumentException("all size values must be > 0 but got " + size);

    this.location = location;
    this.size = size;

    blocks = new Block[size.x][size.y][size.z];
    initBlocks(assetManager, blockAt);

    this.node = new Node();
    node.setLocalTranslation(
        this.location.x * size.x, this.location.y * size.y, this.location.z * size.z);
    initNode(assetManager);
  }

  private void initBlocks(@NonNull AssetManager assetManager, @NonNull BlockAtFunction blockAt) {
    for (int x = 0; x < size.x; x++) {
      for (int y = 0; y < size.y; y++) {
        for (int z = 0; z < size.z; z++) {
          Optional<Block> maybeBlock =
              blockAt.apply(
                  location.x * size.x + x, location.y * size.y + y, location.z * size.z + z);
          if (maybeBlock.isPresent()) {
            setBlock(x, y, z, maybeBlock.get());
          }
        }
      }
    }
  }

  private void initNode(AssetManager assetManager) {
    Node node = new Node();

    for (int z = 0; z < size.z; z++) {
      for (int y = 0; y < size.y; y++) {
        int xStart = 0;
        int xLen = 0;

        for (int x = 0; x < size.x; x++) {
          Block block = getBlock(x, y, z);
          if (block != null) {
            xLen += 1;
            if (x + 1 == size.x || !block.equals(getBlock(x + 1, y, z))) {
              Spatial box =
                  createBox(new Vec3i(xStart, y, z), new Vec3i(xLen, 1, 1), block, assetManager);
              node.attachChild(box);
              xStart = x + 1;
              xLen = 0;
            }
          } else {
            xStart = x + 1;
            xLen = 0;
          }
        }
      }
    }

    this.node.attachChild(node);
  }

  private Block getBlock(int x, int y, int z) {
    return blocks[x][y][z];
  }

  private void setBlock(int x, int y, int z, Block block) {
    blocks[x][y][z] = block;
  }

  private Spatial createBox(
      @NonNull Vec3i location,
      @NonNull Vec3i size,
      @NonNull Block block,
      @NonNull AssetManager assetManager) {
    Box mesh = new Box(size.x / 2f, size.y / 2f, size.z / 2f);
    Geometry geo = new Geometry("Block " + location.x + " " + location.y + " " + location.z, mesh);
    Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    mat.setColor("Color", block.type.color);
    geo.setMaterial(mat);
    geo.setLocalTranslation(
        location.x + size.x / 2f, location.y + size.y / 2f, location.z + size.z / 2f);
    return geo;
  }
}
