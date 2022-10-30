package blocks;

import com.jme3.app.SimpleApplication;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.Light;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.system.AppSettings;
import com.simsilica.mathd.Vec3i;
import lombok.extern.slf4j.Slf4j;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class App extends SimpleApplication {

  private static final int slowMovementSpeed = 1;
  private static final int regularMovementSpeed = 10;
  private static final int spectateMovementSpeed = 250;

  private ChunkBlockGenerator chunkBlockGenerator;
  private PlayerSystem playerSystem;
  private PlayerEntity playerEntity;
  private BitmapText fpsValue;
  private BitmapText memoryValue;
  private float secondCounter = 0;
  private int frameCounter = 0;

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
    cam.setFrustumFar(2048); // default is 1000
    setDisplayStatView(false);
    setDisplayFps(false);

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
    createHud();

    terrainGenerator = new TerrainGenerator(seed);
    chunkBlockGenerator =
        new ChunkBlockGenerator(
            new Vec3i(CHUNK_WIDTH, CHUNK_HEIGHT, CHUNK_DEPTH), terrainGenerator);

    chunkBlockGenerationExecutorService =
        Executors.newFixedThreadPool(8, new ChunkGenerationThreadFactory());
    chunkMeshGenerationExecutorService =
        Executors.newFixedThreadPool(8, new ChunkGenerationThreadFactory());
    initGrid();

    {
      playerSystem = new PlayerSystem(chunkGrid);

      int spawnX = 62;
      int spawnZ = 0;
      Terrain terrainAtSpawn = terrainGenerator.terrainAt(spawnX, spawnZ);
      int scaledHeightAtSpawn = (int) ((terrainAtSpawn.height() + 1) / 2 * WORLD_HEIGHT);
      playerEntity =
          new PlayerEntity(
              new Vector3f(spawnX, scaledHeightAtSpawn + 1, spawnZ)
                  .addLocal(PlayerEntity.size.divide(2)),
              assetManager);
      playerSystem.add(playerEntity);
      rootNode.attachChild(playerEntity.spatial);
    }

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
    guiNode.detachAllChildren();

    for (Light light : rootNode.getLocalLightList()) {
      rootNode.removeLight(light);
    }

    inputManager.deleteMapping("resetGame");
    inputManager.deleteMapping("changeCameraMode");
    inputManager.removeListener((ActionListener) this::debugGameListener);

    inputManager.deleteMapping("recordShiftKeyPress");
    inputManager.removeListener((ActionListener) this::shiftActionListener);

    inputManager.deleteMapping("movePlayerForward");
    inputManager.deleteMapping("movePlayerBackward");
    inputManager.deleteMapping("movePlayerLeft");
    inputManager.deleteMapping("movePlayerRight");
    inputManager.removeListener((ActionListener) this::playerActionListener);

    inputManager.deleteMapping("rotatePlayerLeft");
    inputManager.deleteMapping("rotatePlayerRight");
    inputManager.deleteMapping("rotatePlayerUp");
    inputManager.deleteMapping("rotatePlayerDown");
    inputManager.removeListener((AnalogListener) this::playerAnalogListener);
  }

  private void initInputListeners() {
    inputManager.addMapping("resetGame", new KeyTrigger(KeyInput.KEY_R));
    inputManager.addMapping("changeCameraMode", new KeyTrigger(KeyInput.KEY_SPACE));
    inputManager.addListener(
        (ActionListener) this::debugGameListener, "resetGame", "changeCameraMode");

    inputManager.addMapping("recordShiftKeyPress", new KeyTrigger(KeyInput.KEY_LSHIFT));
    inputManager.addListener((ActionListener) this::shiftActionListener, "recordShiftKeyPress");

    inputManager.addMapping("movePlayerForward", new KeyTrigger(KeyInput.KEY_W));
    inputManager.addMapping("movePlayerBackward", new KeyTrigger(KeyInput.KEY_S));
    inputManager.addMapping("movePlayerLeft", new KeyTrigger(KeyInput.KEY_A));
    inputManager.addMapping("movePlayerRight", new KeyTrigger(KeyInput.KEY_D));
    inputManager.addListener(
        (ActionListener) this::playerActionListener,
        "movePlayerForward",
        "movePlayerBackward",
        "movePlayerLeft",
        "movePlayerRight");

    inputManager.addMapping(
        "rotatePlayerLeft",
        new MouseAxisTrigger(MouseInput.AXIS_X, true),
        new KeyTrigger(KeyInput.KEY_LEFT));
    inputManager.addMapping(
        "rotatePlayerRight",
        new MouseAxisTrigger(MouseInput.AXIS_X, false),
        new KeyTrigger(KeyInput.KEY_RIGHT));
    inputManager.addMapping(
        "rotatePlayerUp",
        new MouseAxisTrigger(MouseInput.AXIS_Y, true),
        new KeyTrigger(KeyInput.KEY_DOWN));
    inputManager.addMapping(
        "rotatePlayerDown",
        new MouseAxisTrigger(MouseInput.AXIS_Y, false),
        new KeyTrigger(KeyInput.KEY_UP));
    inputManager.addListener(
        (AnalogListener) this::playerAnalogListener,
        "rotatePlayerLeft",
        "rotatePlayerRight",
        "rotatePlayerUp",
        "rotatePlayerDown");
  }

  private void shiftActionListener(String name, boolean keyPressed, float tpf) {
    isShiftKeyPressed = keyPressed;
    if (playerEntity.isSpectating) {
      playerEntity.velocity = isShiftKeyPressed ? regularMovementSpeed : spectateMovementSpeed;
    } else {
      playerEntity.velocity = isShiftKeyPressed ? slowMovementSpeed : regularMovementSpeed;
    }
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

      case "changeCameraMode" -> {
        playerEntity.isSpectating = !playerEntity.isSpectating;
        playerEntity.velocity =
            playerEntity.isSpectating ? spectateMovementSpeed : regularMovementSpeed;
      }
    }
  }

  private void playerActionListener(String name, boolean keyPressed, float tpf) {
    switch (name) {
      case "movePlayerForward" -> playerEntity.direction.z += keyPressed ? 1 : -1;
      case "movePlayerBackward" -> playerEntity.direction.z += keyPressed ? -1 : 1;
      case "movePlayerLeft" -> playerEntity.direction.x += keyPressed ? 1 : -1;
      case "movePlayerRight" -> playerEntity.direction.x += keyPressed ? -1 : 1;
    }
  }

  private void playerAnalogListener(String name, float value, float tpf) {
    switch (name) {
      case "rotatePlayerLeft" -> {
        float[] angles = playerEntity.rotation.toAngles(null);
        angles[1] += value;
        playerEntity.rotation.fromAngles(angles);
      }
      case "rotatePlayerRight" -> {
        float[] angles = playerEntity.rotation.toAngles(null);
        angles[1] -= value;
        playerEntity.rotation.fromAngles(angles);
      }
      case "rotatePlayerUp" -> {
        float[] angles = playerEntity.rotation.toAngles(null);
        angles[0] += value;
        playerEntity.rotation.fromAngles(angles);
      }
      case "rotatePlayerDown" -> {
        float[] angles = playerEntity.rotation.toAngles(null);
        angles[0] -= value;
        playerEntity.rotation.fromAngles(angles);
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

  private void createHud() {
    guiFont = assetManager.loadFont("Interface/Fonts/Default.fnt");

    BitmapText fpsLabel = new BitmapText(guiFont);
    fpsLabel.setText("FPS: ");
    fpsLabel.setLocalTranslation(0, settings.getHeight(), 0);
    guiNode.attachChild(fpsLabel);

    fpsValue = new BitmapText(guiFont);
    fpsValue.setLocalTranslation(fpsLabel.getLineWidth(), settings.getHeight(), 0);
    guiNode.attachChild(fpsValue);

    BitmapText memoryLabel = new BitmapText(guiFont);
    memoryLabel.setText("Memory: ");
    memoryLabel.setLocalTranslation(0, settings.getHeight() - fpsLabel.getLineHeight(), 0);
    guiNode.attachChild(memoryLabel);

    memoryValue = new BitmapText(guiFont);
    memoryValue.setLocalTranslation(
        memoryLabel.getLineWidth(), settings.getHeight() - fpsLabel.getLineHeight(), 0);
    guiNode.attachChild(memoryValue);
  }

  // from https://stackoverflow.com/a/3758880/122594
  private static String humanReadableByteCountBin(long bytes) {
    long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
    if (absB < 1024) {
      return bytes + " B";
    }
    long value = absB;
    CharacterIterator ci = new StringCharacterIterator("KMGTPE");
    for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
      value >>= 10;
      ci.next();
    }
    value *= Long.signum(bytes);
    return String.format("%.1f %ciB", value / 1024.0, ci.current());
  }

  @Override
  public void simpleUpdate(float tpf) {
    super.simpleUpdate(tpf);

    memoryValue.setText(
        humanReadableByteCountBin(
            Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));

    {
      secondCounter += getTimer().getTimePerFrame();
      frameCounter++;
      if (secondCounter >= 1.0f) {
        int fps = (int) (frameCounter / secondCounter);
        fpsValue.setText(String.valueOf(fps));
        secondCounter = 0.0f;
        frameCounter = 0;
      }
    }

    chunkGrid.centerAroundWorldLocation(cam.getLocation());
    chunkGrid.update();

    playerSystem.update(tpf);
    animalSystem.update(tpf);

    cam.setLocation(playerEntity.location.add(0, PlayerEntity.size.y / 2, 0));
    cam.setRotation(playerEntity.rotation);
  }

  private static class ChunkGenerationThreadFactory implements ThreadFactory {
    private final AtomicInteger index = new AtomicInteger(1);

    @Override
    public Thread newThread(Runnable r) {
      return new Thread(r, "chunkGenerator-" + index.getAndIncrement());
    }
  }
}
