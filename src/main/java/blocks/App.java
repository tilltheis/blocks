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
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.system.AppSettings;
import com.simsilica.mathd.Vec3i;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

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
  private static final int CHUNK_HEIGHT = 32;
  private static final int CHUNK_DEPTH = 32;

  private static final int GRID_WIDTH = 25;
  private static final int GRID_HEIGHT = 5;
  private static final int GRID_DEPTH = 25;

  private static final int WORLD_HEIGHT = GRID_HEIGHT * CHUNK_HEIGHT;

  ChunkGrid chunkGrid;

  private ExecutorService chunkGenerationExecutorService;

  boolean isShiftKeyPressed = false;

  private float heightNoiseValueAt(int x, int z) {
    long seed = 100;

    Noise mountainNoise = new Noise(4, 0, 1499, 4.1, -4, 0, new Random(seed));
    Noise flatlandNoise = new Noise(4, 0, 1499, 3.5, -4, 0, new Random(seed));

    float mountainValue = mountainNoise.getValue(x, z);
    float flatlandValue = flatlandNoise.getValue(x, z);

    float scaledMountainValue = mountainValue * 1;
    float scaledFlatlandValue = flatlandValue * 0.4f;

    float difference = scaledMountainValue - scaledFlatlandValue;

    float interpolatedValue;
    if (difference > 0 && difference <= 0.2f) {
      float mu = difference * 5;
      interpolatedValue = cosineInterpolation(scaledMountainValue, scaledFlatlandValue, mu);
    } else {
      interpolatedValue = Math.max(scaledMountainValue, scaledFlatlandValue);
    }

    return interpolatedValue;
  }

  // mu is percentage between x and y, must be in range (0, 1)
  private static float cosineInterpolation(float x, float y, float mu) {
    float mu2 = (1 - FastMath.cos(mu * FastMath.PI)) / 2;
    return y * (1 - mu2) + x * mu2;
  }

  @Override
  public void stop() {
    chunkGenerationExecutorService.shutdownNow();
    super.stop();
  }

  @Override
  public void simpleInitApp() {
    flyCam.setMoveSpeed(250);
    cam.setFrustumFar(2048); // default is 1000
    cam.setLocation(
        new Vector3f(GRID_WIDTH * CHUNK_WIDTH / 2f, WORLD_HEIGHT * 1.5f, CHUNK_DEPTH * -3));
    cam.lookAt(
        new Vector3f(
            GRID_WIDTH * CHUNK_WIDTH / 2f, WORLD_HEIGHT / 2f, GRID_DEPTH * CHUNK_DEPTH / 2f),
        new Vector3f(0, GRID_HEIGHT / 2f, 0));

    initInputListeners();

    createCrosshair();

    chunkGenerationExecutorService = Executors.newFixedThreadPool(4);
    initGrid();

    rootNode.addLight(new AmbientLight(new ColorRGBA(0.2f, 0.2f, 0.2f, 1f)));
    rootNode.addLight(
        new DirectionalLight(
            new Vector3f(-0.1f, -1f, -0.1f).normalizeLocal(), new ColorRGBA(2f, 2f, 2f, 1f)));
  }

  private void cleanup() {
    chunkGenerationExecutorService.shutdownNow();

    rootNode.detachAllChildren();

    for (Light light : rootNode.getLocalLightList()) {
      rootNode.removeLight(light);
    }

    inputManager.deleteMapping("resetGame");
    inputManager.removeListener((ActionListener) this::resetGameListener);

    inputManager.deleteMapping("recordShiftKeyPress");
    inputManager.removeListener((ActionListener) this::shiftActionListener);
  }

  private void initInputListeners() {
    inputManager.addMapping("resetGame", new KeyTrigger(KeyInput.KEY_R));
    inputManager.addListener((ActionListener) this::resetGameListener, "resetGame");

    inputManager.addMapping("recordShiftKeyPress", new KeyTrigger(KeyInput.KEY_LSHIFT));
    inputManager.addListener((ActionListener) this::shiftActionListener, "recordShiftKeyPress");
  }

  private void shiftActionListener(String name, boolean keyPressed, float tpf) {
    isShiftKeyPressed = keyPressed;
  }

  private void resetGameListener(String name, boolean keyPressed, float tpf) {
    if (keyPressed) return;

    Vector3f oldCamLocation = cam.getLocation().clone();
    Quaternion oldCamRotation = cam.getRotation().clone();

    cleanup();
    simpleInitApp();

    if (isShiftKeyPressed) {
      cam.setLocation(oldCamLocation);
      cam.setRotation(oldCamRotation);
    }

    System.out.println("resetGame");
  }

  private void initGrid() {
    chunkGrid =
        new ChunkGrid(
            new Vec3i(GRID_WIDTH, GRID_HEIGHT, GRID_DEPTH),
            new Vec3i(CHUNK_WIDTH, CHUNK_HEIGHT, CHUNK_DEPTH),
            cam.getLocation(),
            chunkGenerationExecutorService,
            assetManager,
            this::createBlocks);
    rootNode.attachChild(chunkGrid.getNode());
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

  private static final Block dirtBlock = new Block(BlockType.DIRT, 1f);
  private static final Block[] shadedGrassBlocks =
      IntStream.rangeClosed(1, 10)
          .mapToObj(i -> new Block(BlockType.GRASS, 1f / i))
          .toArray(Block[]::new);

  private Block[][][] createBlocks(Vec3i location) {
    Block[][][] blocks = new Block[CHUNK_WIDTH][CHUNK_HEIGHT][CHUNK_DEPTH];

    for (int x = 0; x < CHUNK_WIDTH; x++) {
      for (int z = 0; z < CHUNK_DEPTH; z++) {
        float height =
            heightNoiseValueAt(location.x * CHUNK_WIDTH + x, location.z * CHUNK_DEPTH + z);
        int scaledHeight = (int) ((height + 1) / 2 * WORLD_HEIGHT);

        for (int y = 0; y < CHUNK_HEIGHT && y <= scaledHeight - (location.y * CHUNK_HEIGHT); y++) {
          Block block;
          if (location.y * CHUNK_HEIGHT + y < scaledHeight) block = dirtBlock;
          else block = shadedGrassBlocks[(int) ((height + 1) / 2 * 10)];
          blocks[x][y][z] = block;
        }
      }
    }

    return blocks;
  }

  @Override
  public void simpleUpdate(float tpf) {
    super.simpleUpdate(tpf);
    chunkGrid.centerAroundWorldLocation(cam.getLocation());
    chunkGrid.update();
  }
}
