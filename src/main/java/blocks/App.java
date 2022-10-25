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
import lombok.extern.slf4j.Slf4j;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class App extends SimpleApplication {

  private ChunkBlockGenerator chunkBlockGenerator;

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

  public static final int WORLD_HEIGHT = GRID_HEIGHT * CHUNK_HEIGHT;

  ChunkGrid chunkGrid;

  private ExecutorService chunkBlockGenerationExecutorService;
  private ExecutorService chunkMeshGenerationExecutorService;

  boolean isShiftKeyPressed = false;

  private static final long seed = 100;
  private TerrainGenerator terrainGenerator;

  private AnimalSystem animalSystem;

  private boolean shouldKeepCamLocation = false;

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

    if (!shouldKeepCamLocation) {
      cam.setLocation(
          new Vector3f(GRID_WIDTH * CHUNK_WIDTH / 2f, WORLD_HEIGHT * 1.5f, CHUNK_DEPTH * -3));
      cam.lookAt(
          new Vector3f(
              GRID_WIDTH * CHUNK_WIDTH / 2f, WORLD_HEIGHT / 2f, GRID_DEPTH * CHUNK_DEPTH / 2f),
          new Vector3f(0, GRID_HEIGHT / 2f, 0));

      cam.setLocation(new Vector3f(0, 150, 0));
    }

    viewPort.setBackgroundColor(ColorRGBA.Blue.clone().interpolateLocal(ColorRGBA.White, 0.15f));

    initInputListeners();

    createCrosshair();

    terrainGenerator = new TerrainGenerator(seed);
    chunkBlockGenerator =
        new ChunkBlockGenerator(
            new Vec3i(CHUNK_WIDTH, CHUNK_HEIGHT, CHUNK_DEPTH), terrainGenerator);

    chunkBlockGenerationExecutorService =
        Executors.newFixedThreadPool(8, new ChunkGenerationThreadFactory());
    chunkMeshGenerationExecutorService =
        Executors.newFixedThreadPool(8, new ChunkGenerationThreadFactory());
    initGrid();

    animalSystem = new AnimalSystem(chunkGrid);

    {
      int spawnX = 62;
      int spawnZ = 0;
      Terrain terrainAtSpawn = terrainGenerator.terrainAt(spawnX, spawnZ);
      int scaledHeightAtSpawn = (int) ((terrainAtSpawn.height() + 1) / 2 * WORLD_HEIGHT);
      AnimalEntity animalEntity =
          new AnimalEntity(
              new Vector3f(0.5f + spawnX, scaledHeightAtSpawn + 1.5f, 0.5f + spawnZ), assetManager);
      animalSystem.add(animalEntity);
      rootNode.attachChild(animalEntity.spatial);
    }

    Random random = new Random(seed);
    for (int i = 0; i < 10; i++) {
      int spawnX = random.nextInt(100);
      int spawnZ = random.nextInt(100);
      Terrain terrainAtSpawn = terrainGenerator.terrainAt(spawnX, spawnZ);
      int scaledHeightAtSpawn = (int) ((terrainAtSpawn.height() + 1) / 2 * WORLD_HEIGHT);
      AnimalEntity animalEntity =
          new AnimalEntity(
              new Vector3f(0.5f + spawnX, scaledHeightAtSpawn + 1.5f, 0.5f + spawnZ), assetManager);
      animalSystem.add(animalEntity);
      rootNode.attachChild(animalEntity.spatial);
    }

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
        shouldKeepCamLocation = isShiftKeyPressed;

        cleanup();
        simpleInitApp();

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
            chunkBlockGenerator::generateBlocks);
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

  @Override
  public void simpleUpdate(float tpf) {
    super.simpleUpdate(tpf);
    chunkGrid.centerAroundWorldLocation(cam.getLocation());
    chunkGrid.update();
    animalSystem.update(tpf);
  }

  private static class ChunkGenerationThreadFactory implements ThreadFactory {
    private final AtomicInteger index = new AtomicInteger(1);

    @Override
    public Thread newThread(Runnable r) {
      return new Thread(r, "chunkGenerator-" + index.getAndIncrement());
    }
  }
}
