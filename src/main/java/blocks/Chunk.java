package blocks;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.*;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.scene.shape.Box;
import com.jme3.util.BufferUtils;
import com.simsilica.mathd.Vec3i;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.text.MessageFormat;
import java.util.*;
import java.util.stream.IntStream;

// TODO optimize https://0fps.net/2012/01/14/an-analysis-of-minecraft-like-engines/
@EqualsAndHashCode
@ToString(onlyExplicitlyIncluded = true)
public class Chunk {
  @ToString.Include @Getter private final Vec3i location;
  @ToString.Include @Getter private final Vec3i size;
  private final Block[][][] blocks;

  @Getter private final Node node;

  private final Map<Vec3i, Quaternion> rotationForDirection =
      Map.of(
          new Vec3i(0, 0, 1),
          new Quaternion().fromAngleAxis(FastMath.PI, Vector3f.UNIT_Y),
          new Vec3i(0, 0, -1),
          new Quaternion().fromAngleAxis(0, Vector3f.UNIT_Y),
          new Vec3i(0, 1, 0),
          new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X),
          new Vec3i(1, 0, 0),
          new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_Y),
          new Vec3i(-1, 0, 0),
          new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Y));
  private final Vector2f[] meshTextureCoordinates = {
    new Vector2f(0, 0), new Vector2f(1, 0), new Vector2f(0, 1), new Vector2f(1, 1)
  };

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
    initBlocks(blockAt);

    this.node = new Node();
    node.setLocalTranslation(
        this.location.x * size.x, this.location.y * size.y, this.location.z * size.z);
    initNode(assetManager);

    naiveInitNode(assetManager);
  }

  private void initBlocks(@NonNull BlockAtFunction blockAt) {
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
      @NonNull Vec3i location,
      @NonNull Vec3i axisDirection,
      @NonNull Vec3i visibilityDirection,
      boolean @NonNull [] @NonNull [] @NonNull [] mask) {
    int length = 1;
    Vec3i nextLocation = location.add(axisDirection);

    while (size.x > nextLocation.x
        && size.y > nextLocation.y
        && size.z > nextLocation.z
        && isVisibleFrom(nextLocation, visibilityDirection)
        && !mask[nextLocation.x][nextLocation.y][nextLocation.z]
        && block.equals(getBlock(nextLocation.x, nextLocation.y, nextLocation.z))) {
      length += 1;
      nextLocation.addLocal(axisDirection);
    }

    return length;
  }

  private boolean isVisibleFrom(@NonNull Vec3i location, @NonNull Vec3i direction) {
    return !hasBlockAt(location.add(direction));
  }

  private boolean hasBlockAt(@NonNull Vec3i location) {
    return location.x > 0
        && location.y > 0
        && location.z > 0
        && location.x < size.x
        && location.y < size.y
        && location.z < size.z
        && blocks[location.x][location.y][location.z] != null;
  }

  Node tmpNode = new Node();

  private void initNode(@NonNull AssetManager assetManager) {
    List<Vector3f> vertices = new ArrayList<>();
    List<Vector2f> textureCoordinates = new ArrayList<>();
    List<Integer> indexes = new ArrayList<>();
    List<Float> normals = new ArrayList<>();

    for (Map.Entry<Vec3i, Quaternion> entry : rotationForDirection.entrySet()) {
      Vec3i direction = entry.getKey();

      boolean[][][] mask = new boolean[size.x][size.y][size.z];

      for (int z = 0; z < size.z; z++) {
        for (int y = 0; y < size.y; y++) {
          for (int x = 0; x < size.x; x++) {
            if (mask[x][y][z]) continue;

            Block block = getBlock(x, y, z);
            Vec3i location = new Vec3i(x, y, z);

            if (block != null && isVisibleFrom(location, direction)) {
              int xLen =
                  equalBlockCountInDirection(block, location, new Vec3i(1, 0, 0), direction, mask);

              int zLen =
                  IntStream.range(0, xLen)
                      .map(
                          offset ->
                              equalBlockCountInDirection(
                                  block,
                                  location.add(offset, 0, 0),
                                  new Vec3i(0, 0, 1),
                                  direction,
                                  mask))
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
                                              location.add(xOffset, 0, zOffset),
                                              new Vec3i(0, 1, 0),
                                              direction,
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

              Quaternion rotation = entry.getValue();
              Vector3f lowerLeftRotation = rotation.mult(new Vector3f(-1, -1, -1));
              Vector3f lowerRightRotation = rotation.mult(new Vector3f(1, -1, -1));
              Vector3f upperLeftRotation = rotation.mult(new Vector3f(-1, 1, -1));
              Vector3f upperRightRotation = rotation.mult(new Vector3f(1, 1, -1));

              int index = vertices.size();

              Collections.addAll(
                  vertices,
                  new Vector3f(
                      x + xLen * (lowerLeftRotation.x > 0 ? 1 : 0),
                      y + yLen * (lowerLeftRotation.y > 0 ? 1 : 0),
                      z + zLen * (lowerLeftRotation.z > 0 ? 1 : 0)),
                  new Vector3f(
                      x + xLen * (lowerRightRotation.x > 0 ? 1 : 0),
                      y + yLen * (lowerRightRotation.y > 0 ? 1 : 0),
                      z + zLen * (lowerRightRotation.z > 0 ? 1 : 0)),
                  new Vector3f(
                      x + xLen * (upperLeftRotation.x > 0 ? 1 : 0),
                      y + yLen * (upperLeftRotation.y > 0 ? 1 : 0),
                      z + zLen * (upperLeftRotation.z > 0 ? 1 : 0)),
                  new Vector3f(
                      x + xLen * (upperRightRotation.x > 0 ? 1 : 0),
                      y + yLen * (upperRightRotation.y > 0 ? 1 : 0),
                      z + zLen * (upperRightRotation.z > 0 ? 1 : 0)));

              Collections.addAll(textureCoordinates, meshTextureCoordinates);

              Collections.addAll(
                  indexes, index + 2, index + 3, index + 1, index + 1, index + 0, index + 2);

              Collections.addAll(
                  normals,
                  (float) direction.x,
                  (float) direction.y,
                  (float) direction.z,
                  (float) direction.x,
                  (float) direction.y,
                  (float) direction.z,
                  (float) direction.x,
                  (float) direction.y,
                  (float) direction.z,
                  (float) direction.x,
                  (float) direction.y,
                  (float) direction.z);
            }
          }
        }
      }
    }

    Spatial box =
        createMesh(
            new Block(BlockType.DIRT),
            vertices,
            textureCoordinates,
            indexes,
            normals,
            assetManager);
    this.node.attachChild(box);
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

  private Spatial createMesh(
      @NonNull Block block,
      @NonNull List<Vector3f> vertices,
      @NonNull List<Vector2f> textureCoordinates,
      @NonNull List<Integer> indexes,
      @NonNull List<Float> normals,
      @NonNull AssetManager assetManager) {
    System.out.println("vertices.size() = " + vertices.size());

    Mesh mesh = new Mesh();

    Vector3f[] verticesArray = vertices.toArray(Vector3f[]::new);
    Vector2f[] texCoordArray = textureCoordinates.toArray(Vector2f[]::new);
    int[] indexesArray = indexes.stream().mapToInt(x -> x).toArray();
    float[] normalsArray = new float[normals.size()];
    for (int i = 0; i < normals.size(); i++) {
      normalsArray[i] = normals.get(i);
    }
    mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(verticesArray));
    mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(texCoordArray));
    mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indexesArray));
    mesh.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(normalsArray));
    mesh.updateBound();

    String name = MessageFormat.format("block={0} location={1} size={2}", block, location, size);
    Geometry geo = new Geometry(name, mesh);

    Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
    mat.setBoolean("UseMaterialColors", true);
    mat.setColor("Ambient", block.type.color);
    mat.setColor("Diffuse", block.type.color);

    geo.setMaterial(mat);
    geo.setLocalTranslation(location.x, location.y, location.z);

    return geo;
  }

  private Spatial createDebugBox(@NonNull AssetManager assetManager) {
    Box box = new Box(.5f, .5f, .5f);
    Geometry geo = new Geometry("floor", box);
    Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    mat.setColor("Color", ColorRGBA.Gray.setAlpha(.25f));
    mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
    geo.setQueueBucket(RenderQueue.Bucket.Transparent);
    geo.setMaterial(mat);
    //    geo.setLocalTranslation(box.xExtent, box.yExtent, box.zExtent);
    geo.setLocalTranslation(box.xExtent + 6, box.yExtent + 10, box.zExtent + 2);
    return geo;
  }

  private void naiveInitNode(AssetManager assetManager) {
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
                  naiveCreateBox(
                      new Vec3i(xStart, y, z), new Vec3i(xLen, 1, 1), block, assetManager);
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

    node.setLocalTranslation(50, 0, 0);

    this.node.attachChild(node);
  }

  private Spatial naiveCreateBox(
      @NonNull Vec3i location,
      @NonNull Vec3i size,
      @NonNull Block block,
      @NonNull AssetManager assetManager) {
    Box mesh = new Box(size.x / 2f, size.y / 2f, size.z / 2f);
    Geometry geo = new Geometry("Block " + location.x + " " + location.y + " " + location.z, mesh);

    Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
    mat.setBoolean("UseMaterialColors", true);
    mat.setColor("Ambient", block.type.color);
    mat.setColor("Diffuse", block.type.color);

    geo.setMaterial(mat);
    geo.setLocalTranslation(location.x, location.y, location.z);
    node.attachChild(geo);
    geo.setLocalTranslation(
        location.x + size.x / 2f, location.y + size.y / 2f, location.z + size.z / 2f);

    return geo;
  }
}
