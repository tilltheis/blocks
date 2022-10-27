package blocks;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;

public class PlayerEntity {
  /** center body location */
  public Vector3f location;

  /** In which direction velocity is applied. Relative to the rotation. */
  public Vector3f direction = new Vector3f(0, 0, 0);

  /** Where player looks at. */
  public Quaternion rotation = Quaternion.IDENTITY.clone();

  public static final Vector3f size = new Vector3f(0.75f, 2, 0.75f);

  /** Movement speed. */
  public float velocity = 10;

  public boolean isSpectating = false;

  public final Spatial spatial;

  public PlayerEntity(Vector3f location, AssetManager assetManager) {
    this.location = location;

    Box bodyBox = new Box(size.x / 2, size.y / 2, size.z / 2);
    Geometry bodyGeometry = new Geometry("player", bodyBox);
    Material bodyMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    bodyMaterial.setColor("Color", ColorRGBA.Red);
    bodyGeometry.setMaterial(bodyMaterial);

    Box headBox = new Box(size.x / 20, size.y / 20, size.z / 20);
    Geometry headGeometry = new Geometry("player", headBox);
    Material headMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    headMaterial.setColor("Color", ColorRGBA.Blue);
    headGeometry.setMaterial(headMaterial);
    headGeometry.setLocalTranslation(0, size.y / 6 * 5 / 2, size.x / 2);

    Node node = new Node();
    node.attachChild(bodyGeometry);
    node.attachChild(headGeometry);
    node.setLocalTranslation(this.location);

    spatial = node;
  }
}
