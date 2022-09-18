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

// TODO optimize https://0fps.net/2012/01/14/an-analysis-of-minecraft-like-engines/
@EqualsAndHashCode
@ToString(onlyExplicitlyIncluded = true)
public class Chunk {
  @ToString.Include public final Vec3i location;
  @ToString.Include public final Vec3i size;
  private final Block[][][] blocks;
  private final AssetManager assetManager;

  @Getter private final Node node = new Node();

  public Chunk(@NonNull Vec3i location, @NonNull Vec3i size, AssetManager assetManager) {
    this.location = location;
    this.size = size;

    blocks = new Block[size.y][size.x][size.z];
    this.assetManager = assetManager;
  }

  public void setBlock(@NonNull Vec3i location, Block block) {
    if (location.x < 0
        || location.y < 0
        || location.z < 0
        || location.x >= size.x
        || location.y >= size.y
        || location.z >= size.z)
      throw new IllegalArgumentException(location + " out of bounds for " + this);

    if (blocks[location.y][location.x][location.z] != null)
      node.detachChildNamed("Block " + location.x + " " + location.y + " " + location.z);

    blocks[location.y][location.x][location.z] = block;
    if (block != null) attachToNode(location, block);
  }

  public void attachToNode(@NonNull Vec3i location, @NonNull Block block) {
    Box mesh = new Box(.5f, .5f, .5f);
    Geometry geo = new Geometry("Block " + location.x + " " + location.y + " " + location.z, mesh);
    Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    mat.setColor("Color", block.type.color);
    geo.setMaterial(mat);
    geo.setLocalTranslation(
        new Vec3i(location.x, location.y, location.z)
            .add(this.location.x * size.x, this.location.y * size.y, this.location.z * size.z)
            .toVector3f());
    node.attachChild(geo);
  }
}
