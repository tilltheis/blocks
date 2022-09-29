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

import java.util.LinkedList;
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
  private static final int CHUNK_HEIGHT = 32;
  private static final int CHUNK_DEPTH = 32;

  private static final int GRID_DIMENSION = 10;
  private static final int GRID_HEIGHT = 3;

  private static final int WORLD_HEIGHT = GRID_HEIGHT * CHUNK_HEIGHT;

  Noise heightNoise;

  LinkedList<LinkedList<LinkedList<Chunk>>> chunks;

  boolean isShiftKeyPressed = false;

  private Vec3i playerGridLocation = new Vec3i(GRID_DIMENSION / 2, 0, GRID_DIMENSION / 2);

  private void initNoise() {
    long seed = 100;
    heightNoise = new Noise(10, 0, 2000, 2, 0, 0, new Random(seed));
  }

  @Override
  public void simpleInitApp() {
    flyCam.setMoveSpeed(250);
    cam.setFrustumFar(2048); // default is 1000
    cam.setLocation(
        new Vector3f(GRID_DIMENSION * CHUNK_WIDTH / 2f, WORLD_HEIGHT * 1.5f, CHUNK_DEPTH * -3));
    cam.lookAt(
        new Vector3f(
            GRID_DIMENSION * CHUNK_WIDTH / 2f,
            WORLD_HEIGHT / 2f,
            GRID_DIMENSION * CHUNK_DEPTH / 2f),
        new Vector3f(0, GRID_HEIGHT / 2f, 0));

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
    playerGridLocation = new Vec3i(GRID_DIMENSION / 2, 0, GRID_DIMENSION / 2);

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
    LinkedList<LinkedList<LinkedList<Chunk>>> xs = new LinkedList<>();
    chunks = xs;

    for (int x = 0; x < GRID_DIMENSION; x++) {
      LinkedList<LinkedList<Chunk>> ys = new LinkedList<>();
      xs.add(ys);

      for (int y = 0; y < GRID_HEIGHT; y++) {
        LinkedList<Chunk> zs = new LinkedList<>();
        ys.add(zs);

        for (int z = 0; z < GRID_DIMENSION; z++) {
          Vec3i location =
              new Vec3i(
                  cam.getLocation()
                      .divide(new Vector3f(CHUNK_WIDTH, 1, CHUNK_DEPTH))
                      .addLocal(
                          GRID_DIMENSION / -2f + x,
                          -cam.getLocation().y + y,
                          GRID_DIMENSION / -2f + z));
          Chunk chunk =
              new Chunk(
                  location,
                  new Vec3i(CHUNK_WIDTH, CHUNK_HEIGHT, CHUNK_DEPTH),
                  createBlocks(location.x, location.y, location.z),
                  assetManager);
          zs.add(chunk);
          rootNode.attachChild(chunk.getNode());
        }
      }
    }
  }

  private static final Block dirtBlock = new Block(BlockType.DIRT);
  private static final Block grassBlock = new Block(BlockType.GRASS);

  private Block[][][] createBlocks(int locationX, int locationY, int locationZ) {
    Block[][][] blocks = new Block[CHUNK_WIDTH][CHUNK_HEIGHT][CHUNK_DEPTH];

    for (int x = 0; x < CHUNK_WIDTH; x++) {
      for (int z = 0; z < CHUNK_DEPTH; z++) {
        float height =
            heightNoise.getValue(
                (locationX * CHUNK_WIDTH + x) * 2 - 100, (locationZ * CHUNK_DEPTH + z) * 2);
        int scaledHeight = (int) ((height + 1) / 2 * WORLD_HEIGHT);

        for (int y = 0; y < CHUNK_HEIGHT && y <= scaledHeight - (locationY * CHUNK_HEIGHT); y++) {
          Block block;
          if (locationY * CHUNK_HEIGHT + y < scaledHeight) block = dirtBlock;
          else block = grassBlock;
          blocks[x][y][z] = block;
        }
      }
    }

    return blocks;
  }

  @Override
  public void simpleUpdate(float tpf) {
    super.simpleUpdate(tpf);

    // round because both (int)-0.9f and (int)+0.9f result in 0, effectively spanning 2 ints
    Vec3i newPlayerGridLocation =
        new Vec3i(
            Math.round(cam.getLocation().x / CHUNK_WIDTH),
            0,
            Math.round((cam.getLocation().z / CHUNK_DEPTH)));

    while (newPlayerGridLocation.x != playerGridLocation.x) {
      boolean isPlus = newPlayerGridLocation.x > playerGridLocation.x;

      // remove
      {
        for (LinkedList<Chunk> zs : (isPlus ? chunks.getFirst() : chunks.getLast())) {
          for (Chunk chunk : zs) {
            rootNode.detachChild(chunk.getNode());
          }
        }

        if (isPlus) chunks.removeFirst();
        else chunks.removeLast();
      }

      // add
      {
        LinkedList<LinkedList<Chunk>> ys = new LinkedList<>();
        if (isPlus) chunks.addLast(ys);
        else chunks.addFirst(ys);

        for (int y = 0; y < GRID_HEIGHT; y++) {
          LinkedList<Chunk> zs = new LinkedList<>();
          ys.add(zs);

          for (int z = 0; z < GRID_DIMENSION; z++) {
            int x = isPlus ? GRID_DIMENSION - 1 : 0;
            Vec3i location =
                new Vec3i(
                    cam.getLocation()
                        .divide(new Vector3f(CHUNK_WIDTH, 1, CHUNK_DEPTH))
                        .addLocal(
                            GRID_DIMENSION / -2f + x,
                            -cam.getLocation().y + y,
                            GRID_DIMENSION / -2f + z));
            Chunk chunk =
                new Chunk(
                    location,
                    new Vec3i(CHUNK_WIDTH, CHUNK_HEIGHT, CHUNK_DEPTH),
                    createBlocks(location.x, location.y, location.z),
                    assetManager);
            zs.add(chunk);
            rootNode.attachChild(chunk.getNode());
          }
        }
      }

      playerGridLocation.x += isPlus ? 1 : -1;
    }

    while (newPlayerGridLocation.z != playerGridLocation.z) {
      boolean isPlus = newPlayerGridLocation.z > playerGridLocation.z;

      int x = 0;
      for (LinkedList<LinkedList<Chunk>> ys : chunks) {
        int y = 0;

        for (LinkedList<Chunk> zs : ys) {
          // remove
          {
            Chunk chunk = isPlus ? zs.getFirst() : zs.getLast();
            rootNode.detachChild(chunk.getNode());

            if (isPlus) zs.removeFirst();
            else zs.removeLast();
          }

          // add
          {
            int z = isPlus ? GRID_DIMENSION - 1 : 0;
            Vec3i location =
                new Vec3i(
                    cam.getLocation()
                        .divide(new Vector3f(CHUNK_WIDTH, 1, CHUNK_DEPTH))
                        .addLocal(
                            GRID_DIMENSION / -2f + x,
                            -cam.getLocation().y + y,
                            GRID_DIMENSION / -2f + z));
            Chunk chunk =
                new Chunk(
                    location,
                    new Vec3i(CHUNK_WIDTH, CHUNK_HEIGHT, CHUNK_DEPTH),
                    createBlocks(location.x, location.y, location.z),
                    assetManager);
            if (isPlus) zs.addLast(chunk);
            else zs.addFirst(chunk);
            rootNode.attachChild(chunk.getNode());
          }

          y += 1;
        }

        x += 1;
      }

      playerGridLocation.z += isPlus ? 1 : -1;
    }
  }
}
