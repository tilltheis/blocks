package blocks;

import com.jme3.app.SimpleApplication;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.math.*;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import com.jme3.system.AppSettings;

import blocks.VoronoiDiagram.Point;
import com.jme3.util.BufferUtils;
import earcut4j.Earcut;

import java.util.List;
import java.util.stream.DoubleStream;

public class App extends SimpleApplication {

  public static void main(String[] args) {
    App app = new App();

    AppSettings settings = new AppSettings(true);
    settings.setTitle("My Awesome Game");
    settings.setResolution(1920, 1080);
    app.setSettings(settings);

    app.setShowSettings(false);

    app.start();
  }

  private World world;

  @Override
  public void simpleInitApp() {
    flyCam.setMoveSpeed(500);
    System.out.println("initial render distance: " + cam.getFrustumFar());
    cam.setFrustumFar(2048);
    //    cam.setLocation(new Vector3f(1024, 800, -1024));
    cam.setLocation(new Vector3f(0, 800, 0));
    //    cam.lookAtDirection(new Vector3f(0, -1, 0), new Vector3f(0, 1, 0));
    cam.lookAt(new Vector3f(0, 0, 0), new Vector3f(0, 1, 0));
    cam.setRotation(new Quaternion(0, 0.75f, -0.75f, 0));

    world = WorldGenerator.generateWorld();

    // floor/background box
    Box mesh = new Box(1200, .5f, 1200);
    Geometry geo = new Geometry("Box", mesh);
    geo.setLocalTranslation(1024, -50, -1024);
    Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    mat.setColor("Color", ColorRGBA.fromRGBA255(10, 10, 10, 255));
    geo.setMaterial(mat);

    rootNode.attachChild(geo);

    for (var biome : world.biomes()) {
      createBiome(biome);
    }
  }

  private void createBiome(Biome biome) {
    ColorRGBA biomeColor = // ColorRGBA.randomColor();
        switch (biome.type().color()) {
          case GREEN -> ColorRGBA.Green;
          case YELLOW -> ColorRGBA.Yellow;
          case BLUE -> ColorRGBA.Blue;
        };

    {
      Point bottomLeft = new Point(0, 0);
      Point topRight = new Point(0, 0);

      for (var point : biome.polygon()) {
        if (point.x() < bottomLeft.x()) bottomLeft = new Point(point.x(), bottomLeft.y());
        if (point.x() > topRight.x()) topRight = new Point(point.x(), topRight.y());
        if (point.y() < bottomLeft.y()) bottomLeft = new Point(bottomLeft.x(), point.y());
        if (point.y() > topRight.y()) topRight = new Point(topRight.x(), point.y());
      }

      double[] flatPolygon =
          biome.polygon().stream()
              .flatMapToDouble(vec2 -> DoubleStream.of(vec2.x() * 1.0d, vec2.y() * 1.0d))
              .toArray();

      Vector3f[] vertices =
          biome.polygon().stream()
              .map(point -> new Vector3f(point.x(), 0, -point.y()))
              .toArray(Vector3f[]::new);
      Vector2f[] textCoord =
          biome.polygon().stream()
              .map(point -> new Vector2f(point.x(), -point.y()))
              .toArray(Vector2f[]::new);
      int[] indexes = Earcut.earcut(flatPolygon).stream().mapToInt(i -> i).toArray();

      Mesh mesh = new Mesh();
      mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(vertices));
      mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(textCoord));
      mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indexes));
      mesh.updateBound();

      Geometry geo = new Geometry("Biome", mesh);
      Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
      mat.setColor("Color", biomeColor);
      geo.setMaterial(mat);

      rootNode.attachChild(geo);
    }

    //    for (Vec2 cell : biome.cells()) {
    //      {
    //        Sphere mesh = new Sphere(10, 10, 5);
    //        Geometry geo = new Geometry("Box", mesh);
    //        geo.setLocalTranslation(cell.x(), 10, -cell.y());
    //        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    //        mat.setColor("Color", ColorRGBA.Black);
    //        geo.setMaterial(mat);
    //
    //        rootNode.attachChild(geo);
    //      }
    //
    //      {
    //        BitmapFont font = assetManager.loadFont("Interface/Fonts/Default.fnt");
    //        BitmapText text = new BitmapText(font);
    //        text.setSize(font.getCharSet().getRenderedSize());
    //        text.setText(cell.toString());
    //        text.setColor(ColorRGBA.Red);
    //        text.setLocalTranslation(cell.x() - 75, 10, -cell.y() - 5);
    //        text.rotateUpTo(new Vector3f(0, 0, (float) Math.PI / -2));
    //
    //        rootNode.attachChild(text);
    //      }
    //    }
  }

  @Override
  public void simpleUpdate(float tpf) {
    super.simpleUpdate(tpf);
  }
}
