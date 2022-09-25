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

import java.text.MessageFormat;
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

  Node tmpNode = new Node();

  private void initNode(AssetManager assetManager) {
    //    Node node = new Node();
    Node node = tmpNode;

    boolean[][][] mask = new boolean[size.x][size.y][size.z];

    for (int z = 0; z < size.z; z++) {
      for (int y = 0; y < size.y; y++) {
        int xStart = 0;
        int xLen = 0;

        for (int x = 0; x < size.x; x++) {
          if (mask[x][y][z]) {
            xStart = x + 1;
            continue;
          }

          Block block = getBlock(x, y, z);
          if (block != null) {
            xLen += 1;
            if (x + 1 == size.x || mask[x + 1][y][z] || !block.equals(getBlock(x + 1, y, z))) {

              int minZLen = size.z - z;

              for (int innerX = x - xLen + 1; innerX < x + 1; innerX++) {
                int zLen = 0;

                for (int innerZ = z; innerZ < z + minZLen; innerZ++) {
                  zLen += 1;
                  if (innerZ + 1 == size.z
                      || mask[innerX][y][innerZ]
                      || !block.equals(getBlock(innerX, y, innerZ + 1))) {
                    minZLen = Math.min(minZLen, zLen);
                  }
                }
              }

              for (int innerX = x - xLen + 1; innerX < x + 1; innerX++) {
                for (int innerZ = z; innerZ < z + minZLen; innerZ++) {
                  mask[innerX][y][innerZ] = true;
                }
              }

              Spatial box =
                  createBox(
                      new Vec3i(xStart, y, z), new Vec3i(xLen, 1, minZLen), block, assetManager);
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

    //    this.node.attachChild(node);
    attachAllChildren();
  }

  public void attachAllChildren() {
    for (Spatial child : tmpNode.getChildren()) {
      node.attachChild(child);
    }
    tmpNode.detachAllChildren();
  }

  public void attachOneChild() {
    if (tmpNode.getQuantity() > 0) {
      Spatial child = tmpNode.detachChildAt(0);
      node.attachChild(child);
      System.out.println("Attached " + child.getName());
    }
  }

  public void detachOneChild() {
    if (node.getQuantity() > 0) {
      Spatial child = node.detachChildAt(node.getQuantity() - 1);
      tmpNode.attachChildAt(child, 0);
      System.out.println("Detached " + child.getName());
    }
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
    Geometry geo =
        new Geometry(
            MessageFormat.format("block={0} location={1} size={2}", block, location, size), mesh);
    Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
    mat.setBoolean("UseMaterialColors", true);
    mat.setColor("Ambient", block.type.color);
    mat.setColor("Diffuse", block.type.color);

    geo.setMaterial(mat);
    geo.setLocalTranslation(
        location.x + size.x / 2f, location.y + size.y / 2f, location.z + size.z / 2f);
    return geo;
  }
}
