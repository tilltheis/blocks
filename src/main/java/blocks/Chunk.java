package blocks;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
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
    this.location = location;
    this.size = size;

    this.node = new Node();
    node.setLocalTranslation(
        this.location.x * size.x, this.location.y * size.y, this.location.z * size.z);

    blocks = new Block[size.x][size.y][size.z];
    initBlocksAndNode(assetManager, blockAt);
  }

  private void initBlocksAndNode(
      @NonNull AssetManager assetManager, @NonNull BlockAtFunction blockAt) {
    for (int x = 0; x < size.x; x++) {
      for (int y = 0; y < size.y; y++) {
        for (int z = 0; z < size.z; z++) {
          Optional<Block> maybeBlock =
              blockAt.apply(
                  location.x * size.x + x, location.y * size.y + y, location.z * size.z + z);
          if (maybeBlock.isPresent()) {
            blocks[x][y][z] = maybeBlock.get();
            attachToNode(location.add(x, y, z), maybeBlock.get(), assetManager);
          }
        }
      }
    }
  }

  private void attachToNode(
      @NonNull Vec3i location, @NonNull Block block, @NonNull AssetManager assetManager) {
    Box mesh = new Box(.5f, .5f, .5f);
    Geometry geo = new Geometry("Block " + location.x + " " + location.y + " " + location.z, mesh);
    Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    mat.setColor("Color", block.type.color);
    geo.setMaterial(mat);
    geo.setLocalTranslation(location.x, location.y, location.z);
    node.attachChild(geo);
  }
}
