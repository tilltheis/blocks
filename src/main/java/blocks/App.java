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
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
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

  private ExecutorService chunkBlockGenerationExecutorService;
  private ExecutorService chunkMeshGenerationExecutorService;

  boolean isShiftKeyPressed = false;

  private static final long seed = 100;
  private TerrainGenerator terrainGenerator;

  @Override
  public void destroy() {
    chunkBlockGenerationExecutorService.shutdownNow();
    chunkMeshGenerationExecutorService.shutdownNow();
    super.destroy();
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

    chunkBlockGenerationExecutorService =
        Executors.newFixedThreadPool(8, new ChunkGenerationThreadFactory());
    chunkMeshGenerationExecutorService =
        Executors.newFixedThreadPool(8, new ChunkGenerationThreadFactory());
    initGrid();

    rootNode.addLight(new AmbientLight(new ColorRGBA(0.2f, 0.2f, 0.2f, 1f)));
    rootNode.addLight(
        new DirectionalLight(
            new Vector3f(-0.1f, -1f, -0.1f).normalizeLocal(), new ColorRGBA(2f, 2f, 2f, 1f)));
  }

  private void cleanup() {
    assetManager.clearCache();

    chunkBlockGenerationExecutorService.shutdownNow();
    chunkMeshGenerationExecutorService.shutdownNow();

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

        log.info("resetGame");
      }
    }
  }

  private void initGrid() {
    chunkGrid =
        new ChunkGrid(
            new Vec3i(GRID_WIDTH, GRID_HEIGHT, GRID_DEPTH),
            new Vec3i(CHUNK_WIDTH, CHUNK_HEIGHT, CHUNK_DEPTH),
            cam.getLocation(),
            chunkBlockGenerationExecutorService,
            chunkMeshGenerationExecutorService,
            new BlockMaterial(assetManager),
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

  private static Temperature[] temperatures = Temperature.values();

  /**
   * @return Blocks, colored for all temperatures, indexed by temperature ordinals
   */
  private static Block[] createTemperaturedBlocks(BlockType blockType) {
    Block[] blocks = new Block[temperatures.length];
    for (Temperature temperature : temperatures) {
      ColorRGBA color =
          switch (temperature) {
            case COLD -> blockType.color.clone().interpolateLocal(ColorRGBA.White, 0.5f);
            case NORMAL -> blockType.color;
            case HOT -> blockType.color.clone().interpolateLocal(ColorRGBA.Red, 0.2f);
          };
      blocks[temperature.ordinal()] = new Block(blockType, color, blockType.color.a < 1);
    }
    return blocks;
  }

  private static final Block[] dirtBlocks = createTemperaturedBlocks(BlockType.DIRT);
  private static final Block[] rockBlocks = createTemperaturedBlocks(BlockType.ROCK);
  private static final Block[] waterBlocks = createTemperaturedBlocks(BlockType.WATER);
  private static final Block[] woodBlocks = createTemperaturedBlocks(BlockType.WOOD);
  private static final Block[] leafBlocks = createTemperaturedBlocks(BlockType.LEAF);
  private static final Block[] grassBlocks = createTemperaturedBlocks(BlockType.GRASS);

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
          if (location.y * CHUNK_HEIGHT + y < scaledHeight) block = dirtBlocks[1];
          else {
            block =
                switch (terrain.terrainType()) {
                  case MOUNTAIN -> rockBlocks[terrain.temperature().ordinal()];
                  case HILL -> dirtBlocks[terrain.temperature().ordinal()];
                  case FLATLAND -> grassBlocks[terrain.temperature().ordinal()];
                  case OCEAN -> dirtBlocks[
                      terrain.temperature().ordinal()]; // will be overridden below
                };
          }
          blocks[x][y][z] = block;
        }

        if (terrain.terrainType() == TerrainType.OCEAN) {
          int scaledLandLevelHeight = (int) ((TerrainGenerator.LAND_LEVEL + 1) / 2 * WORLD_HEIGHT);
          int y = scaledLandLevelHeight - (location.y * CHUNK_HEIGHT);
          if (y >= 0 && y < CHUNK_HEIGHT)
            blocks[x][y][z] = waterBlocks[terrain.temperature().ordinal()];
        }

        if (terrain.flora().isPresent()) {
          int y = scaledHeight - (location.y * CHUNK_HEIGHT);
          switch (terrain.flora().get()) {
            case TREE -> createTreeAt(
                x,
                y,
                z,
                blocks,
                woodBlocks[terrain.temperature().ordinal()],
                leafBlocks[terrain.temperature().ordinal()]);
          }
        }
      }
    }

    // create trees that spawn outside this chunk but reach into it
    int outsideTreeRangeX = Flora.TREE.size.x / 2;
    int outsideTreeRangeZ = Flora.TREE.size.z / 2;
    for (int x = -outsideTreeRangeX; x < CHUNK_WIDTH + outsideTreeRangeX; x++) {
      for (int z = -outsideTreeRangeZ; z < CHUNK_DEPTH + outsideTreeRangeZ; z++) {
        Terrain terrain =
            terrainGenerator.terrainAt(location.x * CHUNK_WIDTH + x, location.z * CHUNK_DEPTH + z);

        if (terrain.flora().isPresent() && terrain.flora().get() == Flora.TREE) {
          int scaledHeight = (int) ((terrain.height() + 1) / 2 * WORLD_HEIGHT);
          int y = scaledHeight - (location.y * CHUNK_HEIGHT);
          createTreeAt(
              x,
              y,
              z,
              blocks,
              woodBlocks[terrain.temperature().ordinal()],
              leafBlocks[terrain.temperature().ordinal()]);
        }

        // inside of chunk has already been scanned
        if (x >= 0 && x < CHUNK_WIDTH && z == -1) z = CHUNK_DEPTH - 1;
      }
    }

    return blocks;
  }

  private void createTreeAt(
      int x, int y, int z, Block[][][] blocks, Block woodBlock, Block leafBlock) {
    Vec3i size = Flora.TREE.size;

    if (y <= -size.y || y >= CHUNK_HEIGHT) return;

    int treeStartX = x - size.x / 2;
    int treeStartY = y;
    int treeStartZ = z - size.z / 2;

    int xOffset = Math.max(0, -treeStartX);
    int yOffset = Math.max(0, -treeStartY);
    int zOffset = Math.max(0, -treeStartZ);

    int xLimit = Math.min(size.x, CHUNK_WIDTH - treeStartX);
    int yLimit = Math.min(size.y, CHUNK_HEIGHT - treeStartY);
    int zLimit = Math.min(size.z, CHUNK_DEPTH - treeStartZ);

    for (int i = xOffset; i < xLimit; i++) {
      int chunkX = treeStartX + i;
      for (int j = yOffset; j < yLimit; j++) {
        int chunkY = treeStartY + j;
        for (int k = zOffset; k < zLimit; k++) {
          int chunkZ = treeStartZ + k;

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
