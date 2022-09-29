package blocks;

import com.jme3.app.SimpleApplication;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.Light;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.system.AppSettings;
import com.simsilica.mathd.Vec3i;

import java.util.Optional;
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

  private static final int CHUNK_WIDTH = 32;
  private static final int CHUNK_HEIGHT = 16;
  private static final int CHUNK_DEPTH = 32;

  private static final int GRID_DIMENSION = 1;

  Noise heightNoise;

  Chunk[][] chunks;

  boolean isShiftKeyPressed = false;

  private void initNoise() {
    long seed = 100;
    heightNoise = new Noise(4, 0, 2000, 2, 0, 0, new Random(seed));
  }

  @Override
  public void simpleInitApp() {
    flyCam.setMoveSpeed(50);
    cam.setFrustumFar(2048); // default is 1000
    cam.setLocation(new Vector3f(CHUNK_WIDTH / 2f, CHUNK_HEIGHT + 15, -30));
    cam.lookAt(
        new Vector3f(CHUNK_WIDTH / 2f, CHUNK_HEIGHT / 2f, CHUNK_DEPTH / 2f), new Vector3f(0, 1, 0));

    initNoise();
    initInputListeners();

    createCrosshair();

    initMap();

    rootNode.addLight(new AmbientLight(new ColorRGBA(0.2f, 0.2f, 0.2f, 1f)));
    rootNode.addLight(
        new DirectionalLight(
            new Vector3f(-0.1f, -1f, -0.1f).normalizeLocal(), new ColorRGBA(2f, 2f, 2f, 1f)));
  }

  private void cleanup() {
    rootNode.detachAllChildren();

    for (Light light : rootNode.getLocalLightList()) {
      rootNode.removeLight(light);
    }

    inputManager.deleteMapping("resetGame");
    inputManager.deleteMapping("changeOctaveCount");
    inputManager.deleteMapping("changeFrequencyDivisor");
    inputManager.deleteMapping("changeLacunarity");
    inputManager.deleteMapping("changeGain");
    inputManager.removeListener((ActionListener) this::mapActionListener);

    inputManager.deleteMapping("recordShiftKeyPress");
    inputManager.removeListener((ActionListener) this::shiftActionListener);
  }

  private void initInputListeners() {
    inputManager.addMapping("resetGame", new KeyTrigger(KeyInput.KEY_R));
    inputManager.addMapping("changeOctaveCount", new KeyTrigger(KeyInput.KEY_O));
    inputManager.addMapping("changeFrequencyDivisor", new KeyTrigger(KeyInput.KEY_F));
    inputManager.addMapping("changeLacunarity", new KeyTrigger(KeyInput.KEY_L));
    inputManager.addMapping("changeGain", new KeyTrigger(KeyInput.KEY_G));
    inputManager.addListener(
        (ActionListener) this::mapActionListener,
        "resetGame",
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

  private void mapActionListener(String name, boolean keyPressed, float tpf) {
    if (keyPressed) return;

    switch (name) {
      case "resetGame" -> {
        cleanup();
        simpleInitApp();
        System.out.println("resetGame");
      }

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
    BitmapText crosshair = new BitmapText(guiFont);
    crosshair.setSize(guiFont.getCharSet().getRenderedSize() * 2);
    crosshair.setText("+");
    crosshair.setLocalTranslation(
        settings.getWidth() / 2f - crosshair.getLineWidth() / 2,
        settings.getHeight() / 2f + crosshair.getLineHeight() / 2,
        0);
    guiNode.attachChild(crosshair);
  }

  private void initMap() {
    chunks = new Chunk[GRID_DIMENSION][GRID_DIMENSION];
    for (int x = 0; x < GRID_DIMENSION; x++) {
      for (int z = 0; z < GRID_DIMENSION; z++) {
        Chunk chunk =
            new Chunk(
                new Vec3i(x, 0, z),
                new Vec3i(CHUNK_WIDTH, CHUNK_HEIGHT, CHUNK_DEPTH),
                assetManager,
                this::blockAt);
        chunks[x][z] = chunk;
        rootNode.attachChild(chunk.getNode());
      }
    }
  }

  private static final Block dirtBlock = new Block(BlockType.DIRT);
  private static final Block grassBlock = new Block(BlockType.GRASS);
  private static final Optional<Block> someDirtBlock = Optional.of(dirtBlock);
  private static final Optional<Block> someGrassBlock = Optional.of(grassBlock);

  private Optional<Block> blockAt(int x, int y, int z) {
    float height = heightNoise.getValue(x * 100, z * 100);
    int scaledHeight = (int) ((height + 1) / 2 * CHUNK_HEIGHT);

    if (y < scaledHeight) return someDirtBlock;
    if (y == scaledHeight) return someGrassBlock;

    return Optional.empty();
  }

  @Override
  public void simpleUpdate(float tpf) {
    super.simpleUpdate(tpf);
  }
}
