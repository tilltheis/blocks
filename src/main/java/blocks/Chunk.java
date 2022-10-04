package blocks;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.*;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.util.BufferUtils;
import com.simsilica.mathd.Vec3i;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.text.MessageFormat;
import java.util.*;

// TODO optimize https://0fps.net/2012/01/14/an-analysis-of-minecraft-like-engines/
@EqualsAndHashCode
@ToString(onlyExplicitlyIncluded = true)
public class Chunk {
  @ToString.Include @Getter private final Vec3i location;
  @ToString.Include @Getter private final Vec3i size;
  private final Block[][][] blocks;

  @Getter private final Node node;

  private static final Vec3i UNIT_X = new Vec3i(1, 0, 0);
  private static final Vec3i UNIT_Y = new Vec3i(0, 1, 0);
  private static final Vec3i UNIT_Z = new Vec3i(0, 0, 1);

  private static final Map<Vec3i, Quaternion> rotationForDirection =
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
  private static final Vector2f[] meshTextureCoordinates = {
    new Vector2f(0, 0), new Vector2f(1, 0), new Vector2f(0, 1), new Vector2f(1, 1)
  };

  public Chunk(
      @NonNull Vec3i location,
      @NonNull Vec3i size,
      @NonNull Block[][][] blocks,
      @NonNull AssetManager assetManager) {
    if (size.x < 1 || size.y < 1 || size.z < 1)
      throw new IllegalArgumentException("all size values must be > 0 but got " + size);
    if (blocks.length != size.x || blocks[0].length != size.y || blocks[0][0].length != size.z)
      throw new IllegalArgumentException(
          "blocks size must match chunk size for chunk at location " + location);

    this.location = location;

    this.size = size;
    this.blocks = blocks;

    this.node = new Node();
    node.setLocalTranslation(
        this.location.x * size.x, this.location.y * size.y, this.location.z * size.z);
    initNode(assetManager);
  }

  private int equalBlockCountInDirection(
      Block block,
      Vec3i location,
      Vec3i axisDirection,
      Vec3i visibilityDirection,
      boolean[][][] mask) {
    int length = 1;
    Vec3i nextLocation = location.add(axisDirection);

    while (nextLocation.x < size.x
        && nextLocation.y < size.y
        && nextLocation.z < size.z
        && isVisibleFrom(block, nextLocation, visibilityDirection)
        && !mask[nextLocation.x][nextLocation.y][nextLocation.z]
        && block.equals(getBlock(nextLocation.x, nextLocation.y, nextLocation.z))) {
      length += 1;
      nextLocation.addLocal(axisDirection);
    }

    return length;
  }

  private boolean isVisibleFrom(Block block, Vec3i location, Vec3i direction) {
    int x = location.x + direction.x;
    int y = location.y + direction.y;
    int z = location.z + direction.z;
    boolean isLocal = x >= 0 && y >= 0 && z >= 0 && x < size.x && y < size.y && z < size.z;
    if (isLocal) {
      Block otherBlock = getBlock(x, y, z);
      return otherBlock == null || (otherBlock.isTransparent() && !block.equals(otherBlock));
    } else {
      return !block.isTransparent(); // TODO replace w/ cross-chunk check when needed
    }
  }

  private void initNode(AssetManager assetManager) {
    Map<Block, MeshData> blockToMeshData = new HashMap<>();
    Vec3i inMeshSize = new Vec3i();
    Vec3i location = new Vec3i();

    for (Map.Entry<Vec3i, Quaternion> entry : rotationForDirection.entrySet()) {
      Vec3i direction = entry.getKey();
      Quaternion rotation = entry.getValue();

      boolean[][][] mask = new boolean[size.x][size.y][size.z];

      for (int z = 0; z < size.z; z++) {
        for (int y = 0; y < size.y; y++) {
          for (int x = 0; x < size.x; x++) {
            if (mask[x][y][z]) continue;

            Block block = getBlock(x, y, z);
            location.set(x, y, z);

            if (block != null && isVisibleFrom(block, location, direction)) {
              greedyMeshSize(block, location, direction, mask, inMeshSize);
              updateMeshData(blockToMeshData, rotation, block, direction, location, inMeshSize);
              x += inMeshSize.x - 1;
            }
          }
        }
      }
    }

    for (Map.Entry<Block, MeshData> entry : blockToMeshData.entrySet()) {
      Spatial mesh =
          createMesh(
              entry.getKey(),
              entry.getValue().vertices,
              entry.getValue().textureCoordinates,
              entry.getValue().indexes,
              entry.getValue().normals,
              assetManager);
      this.node.attachChild(mesh);
    }
  }

  private void greedyMeshSize(
      Block block, Vec3i location, Vec3i direction, boolean[][][] mask, Vec3i outMeshSize) {
    int xLen = equalBlockCountInDirection(block, location, UNIT_X, direction, mask);

    int zLen = Integer.MAX_VALUE;
    for (int offset = 0; offset < xLen; offset++) {
      int count =
          equalBlockCountInDirection(block, location.add(offset, 0, 0), UNIT_Z, direction, mask);
      if (count < zLen) zLen = count;
    }

    int yLen = Integer.MAX_VALUE;
    for (int xOffset = 0; xOffset < xLen; xOffset++) {
      for (int zOffset = 0; zOffset < zLen; zOffset++) {
        int count =
            equalBlockCountInDirection(
                block, location.add(xOffset, 0, zOffset), UNIT_Y, direction, mask);
        if (count < yLen) yLen = count;
      }
    }

    for (int i = 0; i < xLen; i++) {
      for (int k = 0; k < zLen; k++) {
        for (int j = 0; j < yLen; j++) {
          mask[location.x + i][location.y + j][location.z + k] = true;
        }
      }
    }

    outMeshSize.x = xLen;
    outMeshSize.y = yLen;
    outMeshSize.z = zLen;
  }

  private void updateMeshData(
      Map<Block, MeshData> blockToMeshData,
      Quaternion rotation,
      Block block,
      Vec3i direction,
      Vec3i location,
      Vec3i length) {
    Vector3f lowerLeftRotation = rotation.mult(new Vector3f(-0.5f, -0.5f, -0.5f));
    Vector3f lowerRightRotation = rotation.mult(new Vector3f(0.5f, -0.5f, -0.5f));
    Vector3f upperLeftRotation = rotation.mult(new Vector3f(-0.5f, 0.5f, -0.5f));
    Vector3f upperRightRotation = rotation.mult(new Vector3f(0.5f, 0.5f, -0.5f));

    MeshData meshData = blockToMeshData.computeIfAbsent(block, b -> MeshData.empty());

    int index = meshData.vertices.size();

    Vector3f lengthVector3f = length.toVector3f();
    Vector3f locationVector3f = location.toVector3f();

    Collections.addAll(
        meshData.vertices,
        locationVector3f.add(lengthVector3f.mult(lowerLeftRotation.add(0.5f, 0.5f, 0.5f))),
        locationVector3f.add(lengthVector3f.mult(lowerRightRotation.add(0.5f, 0.5f, 0.5f))),
        locationVector3f.add(lengthVector3f.mult(upperLeftRotation.add(0.5f, 0.5f, 0.5f))),
        locationVector3f.add(lengthVector3f.mult(upperRightRotation.add(0.5f, 0.5f, 0.5f))));

    Collections.addAll(meshData.textureCoordinates, meshTextureCoordinates);

    Collections.addAll(
        meshData.indexes, index + 2, index + 3, index + 1, index + 1, index + 0, index + 2);

    Collections.addAll(
        meshData.normals,
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

  private Block getBlock(int x, int y, int z) {
    return blocks[x][y][z];
  }

  private void setBlock(int x, int y, int z, Block block) {
    blocks[x][y][z] = block;
  }

  private Spatial createMesh(
      Block block,
      List<Vector3f> vertices,
      List<Vector2f> textureCoordinates,
      List<Integer> indexes,
      List<Float> normals,
      AssetManager assetManager) {
    //    System.out.println("vertices.size() = " + vertices.size());

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
    Geometry geometry = new Geometry(name, mesh);

    Material material;

    if (block.type() == BlockType.WATER) {
      material = new Material(assetManager, "Water.j3md");
    } else {
      material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
      material.setBoolean("UseMaterialColors", true);
      ColorRGBA color = block.type().color.mult(block.brightness());
      material.setColor("Ambient", color);
      material.setColor("Diffuse", color);
    }

    if (block.isTransparent()) {
      material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
      geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
    }

    geometry.setMaterial(material);

    return geometry;
  }

  private record MeshData(
      List<Vector3f> vertices,
      List<Vector2f> textureCoordinates,
      List<Integer> indexes,
      List<Float> normals) {
    public static MeshData empty() {
      return new MeshData(
          new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }
  }
}
