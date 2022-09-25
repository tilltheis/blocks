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
import java.util.stream.IntStream;

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

  private int equalBlockCountInDirection(
      @NonNull Block block,
      @NonNull Vec3i start,
      @NonNull Vec3i step,
      boolean[][] @NonNull [] mask) {
    int length = 1;
    Vec3i nextPosition = start.add(step);

    while (size.x > nextPosition.x
        && size.y > nextPosition.y
        && size.z > nextPosition.z
        && !mask[nextPosition.x][nextPosition.y][nextPosition.z]
        && block.equals(getBlock(nextPosition.x, nextPosition.y, nextPosition.z))) {
      length += 1;
      nextPosition.addLocal(step);
    }

    return length;
  }

  Node tmpNode = new Node();

  private void initNode(@NonNull AssetManager assetManager) {
    //    Node node = new Node();
    Node node = tmpNode;

    boolean[][][] mask = new boolean[size.x][size.y][size.z];

    for (int z = 0; z < size.z; z++) {
      for (int y = 0; y < size.y; y++) {
        for (int x = 0; x < size.x; x++) {
          if (mask[x][y][z]) continue;

          Block block = getBlock(x, y, z);
          if (block != null) {
            Vec3i start = new Vec3i(x, y, z);
            int xLen = equalBlockCountInDirection(block, start, new Vec3i(1, 0, 0), mask);

            int zLen =
                IntStream.range(0, xLen)
                    .map(
                        offset ->
                            equalBlockCountInDirection(
                                block, start.add(offset, 0, 0), new Vec3i(0, 0, 1), mask))
                    .min()
                    .getAsInt();

            int yLen =
                IntStream.range(0, xLen)
                    .flatMap(
                        xOffset ->
                            IntStream.range(0, zLen)
                                .map(
                                    zOffset ->
                                        equalBlockCountInDirection(
                                            block,
                                            start.add(xOffset, 0, zOffset),
                                            new Vec3i(0, 1, 0),
                                            mask)))
                    .min()
                    .getAsInt();

            for (int i = 0; i < xLen; i++) {
              for (int k = 0; k < zLen; k++) {
                for (int j = 0; j < yLen; j++) {
                  mask[x + i][y + j][z + k] = true;
                }
              }
            }

            Spatial box =
                createBox(new Vec3i(x, y, z), new Vec3i(xLen, yLen, zLen), block, assetManager);
            node.attachChild(box);
          }
        }
      }
    }

    System.out.println("node.getQuantity() = " + node.getQuantity());

    this.node.attachChild(node);
    //        attachAllChildren();
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
