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
    settings.setTitle("Blocks");
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
  private TerrainGenerator terrainGenerator;

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

    viewPort.setBackgroundColor(ColorRGBA.Blue.clone().interpolateLocal(ColorRGBA.White, 0.15f));

    initInputListeners();

    createCrosshair();

    terrainGenerator = new TerrainGenerator(seed);

    chunkGenerationExecutorService =
        Executors.newFixedThreadPool(8, new ChunkGenerationThreadFactory());
    initGrid();

    rootNode.addLight(new AmbientLight(new ColorRGBA(0.2f, 0.2f, 0.2f, 1f)));
    rootNode.addLight(
        new DirectionalLight(
            new Vector3f(-0.1f, -1f, -0.1f).normalizeLocal(), new ColorRGBA(2f, 2f, 2f, 1f)));
  }

  private void cleanup() {
    assetManager.clearCache();

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
    if (keyPressed) flyCam.setMoveSpeed(10);
    else flyCam.setMoveSpeed(250);
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
  private static final Block woodBlock = new Block(BlockType.WOOD, 1f, false);
  private static final Block leafBlock = new Block(BlockType.GRASS, 0.7f, true);
  private static final Block[] shadedGrassBlocks =
      IntStream.rangeClosed(1, 10)
          .mapToObj(i -> new Block(BlockType.GRASS, 1f / i, false))
          .toArray(Block[]::new);

  private Block[][][] createBlocks(Vec3i location) {
    Block[][][] blocks = new Block[CHUNK_WIDTH][CHUNK_HEIGHT][CHUNK_DEPTH];

    for (int x = 0; x < CHUNK_WIDTH; x++) {
      for (int z = 0; z < CHUNK_DEPTH; z++) {
        Terrain terrain =
            terrainGenerator.terrainAt(location.x * CHUNK_WIDTH + x, location.z * CHUNK_DEPTH + z);
        float height = terrain.height();
        int scaledHeight = (int) ((height + 1) / 2 * WORLD_HEIGHT);

        for (int y = 0; y < CHUNK_HEIGHT && y <= scaledHeight - (location.y * CHUNK_HEIGHT); y++) {
          Block block;
          if (location.y * CHUNK_HEIGHT + y < scaledHeight) block = dirtBlock;
          else {
            block =
                switch (terrain.terrainType()) {
                  case MOUNTAIN -> rockBlock;
                  case HILL -> dirtBlock;
                  case FLATLAND -> shadedGrassBlocks[(int) ((height + 1) / 2 * 10)];
                  case OCEAN -> dirtBlock;
                };
          }
          blocks[x][y][z] = block;
        }

        if (terrain.terrainType() == TerrainType.OCEAN) {
          int scaledLandLevelHeight = (int) ((TerrainGenerator.LAND_LEVEL + 1) / 2 * WORLD_HEIGHT);
          int y = scaledLandLevelHeight - (location.y * CHUNK_HEIGHT);
          if (y >= 0 && y < CHUNK_HEIGHT) blocks[x][y][z] = waterBlock;
        }

        if (terrain.flora().isPresent()) {
          int y = scaledHeight - (location.y * CHUNK_HEIGHT);
          if (y >= 0 && y < CHUNK_HEIGHT) {
            switch (terrain.flora().get()) {
              case TREE -> createTreeAt(x, y, z, blocks);
            }
          }
        }
      }
    }

    return blocks;
  }

  // for now: don't create tree if parts of it would outreach the current chunk
  // for later: only create parts of tree that fit current chunk and tell chunk grid to also create
  //            it in adjacent chunks eg by simply passing the model block array w/ its coords on
  //            and let chunk grid figure everything out
  private void createTreeAt(int x, int y, int z, Block[][][] blocks) {
    Vec3i size = Flora.TREE.size;

    if (x - (size.x - 1) / 2 < 0
        || y < 0
        || z - (size.z - 1) / 2 < 0
        || x + size.x / 2 >= CHUNK_WIDTH
        || y + size.y >= CHUNK_HEIGHT
        || z + size.z / 2 >= CHUNK_DEPTH) {
      return;
    }

    for (int i = 0; i < size.x; i++) {
      int chunkX = x - (size.x - 1) / 2 + i;
      for (int j = 0; j < size.y; j++) {
        int chunkY = y + j;
        for (int k = 0; k < size.z; k++) {
          int chunkZ = z - (size.z - 1) / 2 + k;

          Block block = null;
          if (i == size.x / 2 && k == size.z / 2) {
            block = j == size.y - 1 ? leafBlock : woodBlock;
          } else if (j >= size.y - 2) {
            if (i > 0 && i < size.x - 1 && k > 0 && k < size.z - 1) {
              block = leafBlock;
            }
          } else if (j >= size.y - 4) {
            block = leafBlock;
          }

          if (block != null && blocks[chunkX][chunkY][chunkZ] != woodBlock) {
            blocks[chunkX][chunkY][chunkZ] = block;
          }
        }
      }
    }
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
