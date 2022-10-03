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
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.system.AppSettings;
import com.simsilica.mathd.Vec3i;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
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

  private static final int GRID_WIDTH = 40;
  private static final int GRID_HEIGHT = 5;
  private static final int GRID_DEPTH = 40;

  private static final int WORLD_HEIGHT = GRID_HEIGHT * CHUNK_HEIGHT;

  ChunkGrid chunkGrid;

  private ExecutorService chunkGenerationExecutorService;

  boolean isShiftKeyPressed = false;

  private static final long seed = 100;
  private final TerrainGenerator terrainGenerator = new TerrainGenerator(seed);

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

    chunkGenerationExecutorService =
        Executors.newFixedThreadPool(8, new ChunkGenerationThreadFactory());
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
    inputManager.removeListener((ActionListener) this::debugGameListener);

    inputManager.deleteMapping("recordShiftKeyPress");
    inputManager.removeListener((ActionListener) this::shiftActionListener);
  }

  private void initInputListeners() {
    inputManager.addMapping("resetGame", new KeyTrigger(KeyInput.KEY_R));
    inputManager.addListener((ActionListener) this::debugGameListener, "resetGame");

    inputManager.addMapping("recordShiftKeyPress", new KeyTrigger(KeyInput.KEY_LSHIFT));
    inputManager.addListener((ActionListener) this::shiftActionListener, "recordShiftKeyPress");
  }

  private void shiftActionListener(String name, boolean keyPressed, float tpf) {
    isShiftKeyPressed = keyPressed;
  }

  private void debugGameListener(String name, boolean keyPressed, float tpf) {
    if (keyPressed) return;

    switch (name) {
      case "resetGame" -> {
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
    }
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

  private static final Block dirtBlock = new Block(BlockType.DIRT, 1f, false);
  private static final Block rockBlock = new Block(BlockType.ROCK, 1f, false);
  private static final Block waterBlock = new Block(BlockType.WATER, 1f, true);
  private static final Block[] shadedGrassBlocks =
      IntStream.rangeClosed(1, 10)
          .mapToObj(i -> new Block(BlockType.GRASS, 1f / i, false))
          .toArray(Block[]::new);

  private Block[][][] createBlocks(Vec3i location) {
    Block[][][] blocks = new Block[CHUNK_WIDTH][CHUNK_HEIGHT][CHUNK_DEPTH];

    for (int x = 0; x < CHUNK_WIDTH; x++) {
      for (int z = 0; z < CHUNK_DEPTH; z++) {
        TerrainHeight terrainHeight =
            terrainGenerator.terrainHeightAt(
                location.x * CHUNK_WIDTH + x, location.z * CHUNK_DEPTH + z);
        Terrain terrain = terrainHeight.terrain();
        float height = terrainHeight.height();
        int scaledHeight = (int) ((height + 1) / 2 * WORLD_HEIGHT);

        for (int y = 0; y < CHUNK_HEIGHT && y <= scaledHeight - (location.y * CHUNK_HEIGHT); y++) {
          Block block;
          if (location.y * CHUNK_HEIGHT + y < scaledHeight) block = dirtBlock;
          else {
            block =
                switch (terrain) {
                  case MOUNTAIN -> rockBlock;
                  case HILL -> dirtBlock;
                  case FLATLAND -> shadedGrassBlocks[(int) ((height + 1) / 2 * 10)];
                  case OCEAN -> dirtBlock;
                };
          }
          blocks[x][y][z] = block;
        }

        if (terrain == Terrain.OCEAN) {
          int scaledLandLevelHeight = (int) ((TerrainGenerator.LAND_LEVEL + 1) / 2 * WORLD_HEIGHT);
          int y = scaledLandLevelHeight - (location.y * CHUNK_HEIGHT);
          if (y < CHUNK_HEIGHT) blocks[x][y][z] = waterBlock;
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

  private static class ChunkGenerationThreadFactory implements ThreadFactory {
    private final AtomicInteger index = new AtomicInteger(1);

    @Override
    public Thread newThread(Runnable r) {
      return new Thread(r, "chunkGenerator-" + index.getAndIncrement());
    }
  }
}
