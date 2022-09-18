package blocks;

import com.jme3.app.SimpleApplication;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.system.AppSettings;

import java.util.Random;

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

  Noise heightNoise;

  boolean isShiftKeyPressed = false;

  private void initNoise() {
    long seed = 100;
    heightNoise = new Noise(4, 0, 2000, 2, 0, 0, new Random(seed));
  }

  @Override
  public void simpleInitApp() {
    flyCam.setMoveSpeed(500);
    System.out.println("initial render distance: " + cam.getFrustumFar());
    cam.setFrustumFar(2048);
    cam.setLocation(new Vector3f(0, 800, 0));
    cam.lookAt(new Vector3f(0, 0, 0), new Vector3f(0, 1, 0));
    cam.setRotation(new Quaternion(0, 0.75f, -0.75f, 0));

    initNoise();
    initInputListeners();

    createCrosshair();

    initMap();
  }

  private void initInputListeners() {
    inputManager.addMapping("regenerateMap", new KeyTrigger(KeyInput.KEY_R));
    inputManager.addMapping("changeOctaveCount", new KeyTrigger(KeyInput.KEY_O));
    inputManager.addMapping("changeFrequencyDivisor", new KeyTrigger(KeyInput.KEY_F));
    inputManager.addMapping("changeLacunarity", new KeyTrigger(KeyInput.KEY_L));
    inputManager.addMapping("changeGain", new KeyTrigger(KeyInput.KEY_G));
    inputManager.addListener(
        (ActionListener) this::actionListener,
        "regenerateMap",
        "changeOctaveCount",
        "changeFrequencyDivisor",
        "changeLacunarity",
        "changeGain");

    inputManager.addMapping("recordShiftKeyPress", new KeyTrigger(KeyInput.KEY_LSHIFT));
    inputManager.addListener((ActionListener) this::shiftActionListener, "recordShiftKeyPress");
  }

  private void shiftActionListener(String name, boolean keyPressed, float tpf) {
    isShiftKeyPressed = keyPressed;
  }

  private void actionListener(String name, boolean keyPressed, float tpf) {
    if (keyPressed) return;

    switch (name) {
      case "regenerateMap" -> System.out.println("regenerateMap");

      case "changeOctaveCount" -> {
        heightNoise.octaves =
            isShiftKeyPressed ? Math.max(1, heightNoise.octaves - 1) : heightNoise.octaves + 1;
        System.out.println("octaves = " + heightNoise.octaves);
      }

      case "changeFrequencyDivisor" -> {
        heightNoise.frequencyDivisor =
            isShiftKeyPressed
                ? Math.max(1, heightNoise.frequencyDivisor - 10)
                : heightNoise.frequencyDivisor + 10;
        System.out.println("frequencyMultiplier = " + heightNoise.frequencyDivisor);
      }

      case "changeLacunarity" -> {
        heightNoise.lacunarity =
            isShiftKeyPressed
                ? Math.max(0.1, heightNoise.lacunarity - 0.1)
                : heightNoise.lacunarity + 0.1;
        System.out.println("lacunarity = " + heightNoise.lacunarity);
      }

      case "changeGain" -> {
        heightNoise.gain =
            isShiftKeyPressed ? Math.max(0, heightNoise.gain - 0.01) : heightNoise.gain + 0.01;
        System.out.println("gain = " + heightNoise.gain);
      }
    }

    initNoise();

    rootNode.detachAllChildren();
    initMap();
  }

  private void createCrosshair() {
    guiFont = assetManager.loadFont("Interface/Fonts/Default.fnt");
    BitmapText ch = new BitmapText(guiFont);
    ch.setSize(guiFont.getCharSet().getRenderedSize() * 2);
    ch.setText("+");
    ch.setLocalTranslation(
        settings.getWidth() / 2 - ch.getLineWidth() / 2,
        settings.getHeight() / 2 + ch.getLineHeight() / 2,
        0);
    guiNode.attachChild(ch);
  }

  private void initMap() {
    Box mesh = new Box(500, .5f, 500);
    Geometry geo = new Geometry("Box", mesh);
    Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    mat.setColor("Color", ColorRGBA.fromRGBA255(10, 10, 10, 255));
    geo.setMaterial(mat);
    rootNode.attachChild(geo);
  }

  @Override
  public void simpleUpdate(float tpf) {
    super.simpleUpdate(tpf);
  }
}
