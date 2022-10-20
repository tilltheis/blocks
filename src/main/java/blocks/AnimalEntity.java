package blocks;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;

import java.util.Random;

public class AnimalEntity {
  /** center body location */
  public Vector3f location;

  public Vector3f direction = new Vector3f(1, 0, 0);

  public float timeLeftToMoveInDirection = 0;

  public final Vector3f size = new Vector3f(1, 1, 1);

  public final Random random;

  public final Spatial spatial;

  public AnimalEntity(Vector3f location, AssetManager assetManager) {
    this.location = location;

    random = new Random((long) (location.x * location.y * location.z));

    Box bodyBox = new Box(size.x / 2, size.y / 2, size.z / 2);
    Geometry bodyGeometry = new Geometry("scorpion", bodyBox);
    Material bodyMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    bodyMaterial.setColor("Color", ColorRGBA.Red);
    bodyGeometry.setMaterial(bodyMaterial);

    Box headBox = new Box(size.x / 20, size.y / 20, size.z / 20);
    Geometry headGeometry = new Geometry("scorpion", headBox);
    Material headMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    headMaterial.setColor("Color", ColorRGBA.Blue);
    headGeometry.setMaterial(headMaterial);
    headGeometry.setLocalTranslation(size.x / 2, 0, 0);

    Node node = new Node();
    node.attachChild(bodyGeometry);
    node.attachChild(headGeometry);
    node.setLocalTranslation(this.location);

    spatial = node;
  }
}
