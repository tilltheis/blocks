package blocks;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.image.PixelWriter;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.converter.NumberStringConverter;

import java.util.Optional;
import java.util.Random;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public class MapApp extends Application {

  public static void main(String[] args) {
    launch();
  }

  Canvas canvas;

  Noise heightNoise;
  Noise heatNoise;

  boolean showTerrain = true;
  boolean showHeight = true;
  boolean showHeat = true;

  double zoom = 100;
  long heightSeed = 100;
  long heatSeed = 200;

  @Override
  public void start(Stage stage) {
    canvas = new Canvas();

    heightNoise = new Noise(4, 0, 140, 2, 0, 10, new Random(heightSeed));
    heatNoise = new Noise(4, 0, 100, 1.2, 0.5, 3, new Random(heatSeed));

    GridPane heightGridPane = createNoiseParamUi("Height", heightNoise);
    GridPane heatGridPane = createNoiseParamUi("Heat", heatNoise);
    GridPane mapGridPane = createMapParamUi();

    HBox settingsHBox = new HBox(heightGridPane, heatGridPane, mapGridPane);
    Scene scene = new Scene(new VBox(settingsHBox, canvas));
    stage.setTitle("Blocks Map");
    stage.setResizable(false);
    stage.setScene(scene);
    stage.show();

    canvas.setWidth(settingsHBox.getWidth());
    canvas.setHeight(settingsHBox.getWidth() * 2 / 3);
    stage.setHeight(stage.getHeight() + canvas.getHeight());

    drawCanvas();
  }

  private GridPane createMapParamUi() {
    Label titleLabel = new Label("Map Params");
    titleLabel.setStyle("-fx-font-weight: bold");
    Label showHeightLabel = new Label("Show Height");
    CheckBox showHeightCheckBox = new CheckBox();
    Label showTerrainLabel = new Label("Show Terrain");
    CheckBox showTerrainCheckBox = new CheckBox();
    Label showHeatLabel = new Label("Show Heat");
    CheckBox showHeatCheckBox = new CheckBox();
    Label zoomLabel = new Label("Zoom");
    TextField zoomTextField = new TextField(Double.toString(zoom));
    Label heightSeedLabel = new Label("Height Seed");
    TextField heightSeedTextField = new TextField(Long.toString(heightSeed));
    Label heatSeedLabel = new Label("Heat Seed");
    TextField heatTextField = new TextField(Long.toString(heatSeed));

    showHeightCheckBox.setSelected(true);
    showTerrainCheckBox.setSelected(true);
    showHeatCheckBox.setSelected(true);

    showHeightCheckBox
        .selectedProperty()
        .addListener(
            (x, y, isChecked) -> {
              showHeight = isChecked;
              drawCanvas();
            });
    showTerrainCheckBox
        .selectedProperty()
        .addListener(
            (x, y, isChecked) -> {
              showTerrain = isChecked;
              drawCanvas();
            });
    showHeatCheckBox
        .selectedProperty()
        .addListener(
            (x, y, isChecked) -> {
              showHeat = isChecked;
              drawCanvas();
            });
    initListeners(zoomTextField, zoom, 10, () -> zoom, x -> zoom = x);
    initListeners(
        heightSeedTextField,
        heightSeed,
        1,
        () -> heightSeed,
        x -> {
          heightSeed = (long) x;
          heightNoise.setRandom(new Random(heightSeed));
          drawCanvas();
        });
    initListeners(
        heatTextField,
        heatSeed,
        1,
        () -> heatSeed,
        x -> {
          heatSeed = (long) x;
          heatNoise.setRandom(new Random(heatSeed));
          drawCanvas();
        });

    GridPane gridPane = new GridPane();
    gridPane.setPadding(new Insets(10));
    gridPane.setHgap(4);
    gridPane.setVgap(8);

    gridPane.add(titleLabel, 0, 0, 2, 1);
    gridPane.add(showHeightLabel, 0, 1);
    gridPane.add(showHeightCheckBox, 1, 1);
    gridPane.add(showTerrainLabel, 0, 2);
    gridPane.add(showTerrainCheckBox, 1, 2);
    gridPane.add(showHeatLabel, 0, 3);
    gridPane.add(showHeatCheckBox, 1, 3);
    gridPane.add(zoomLabel, 0, 4);
    gridPane.add(zoomTextField, 1, 4);
    gridPane.add(heightSeedLabel, 0, 5);
    gridPane.add(heightSeedTextField, 1, 5);
    gridPane.add(heatSeedLabel, 0, 6);
    gridPane.add(heatTextField, 1, 6);

    return gridPane;
  }

  private GridPane createNoiseParamUi(String name, Noise noise) {
    Label titleLabel = new Label(name + " Noise Params");
    titleLabel.setStyle("-fx-font-weight: bold");
    Label octavesLabel = new Label("Octaves");
    TextField octavesTextField = new TextField(Integer.toString(noise.octaves));
    Label startAmplitudeLabel = new Label("Start Amplitude");
    TextField startAmplitudeTextField = new TextField(Double.toString(noise.startAmplitude));
    Label frequencyDivisorLabel = new Label("Frequency Divisor");
    TextField frequencyDivisorTextField = new TextField(Double.toString(noise.frequencyDivisor));
    Label lacunarityLabel = new Label("Lacunarity");
    TextField lacunarityTextField = new TextField(Double.toString(noise.lacunarity));
    Label gainLabel = new Label("Gain");
    TextField gainTextField = new TextField(Double.toString(noise.gain));
    Label granularityLabel = new Label("Granularity");
    TextField granularityTextField = new TextField(Double.toString(noise.granularity));

    initListeners(
        octavesTextField, noise.octaves, 1, () -> noise.octaves, x -> noise.octaves = (int) x);
    initListeners(
        startAmplitudeTextField,
        noise.startAmplitude,
        0.1,
        () -> noise.startAmplitude,
        x -> noise.startAmplitude = x);
    initListeners(
        frequencyDivisorTextField,
        noise.frequencyDivisor,
        10,
        () -> noise.frequencyDivisor,
        x -> noise.frequencyDivisor = x);
    initListeners(
        lacunarityTextField,
        noise.lacunarity,
        0.2,
        () -> noise.lacunarity,
        x -> noise.lacunarity = x);
    initListeners(gainTextField, noise.gain, 0.1, () -> noise.gain, x -> noise.gain = x);
    initListeners(
        granularityTextField,
        noise.granularity,
        0.1,
        () -> noise.granularity,
        x -> noise.granularity = x);

    GridPane gridPane = new GridPane();
    gridPane.setPadding(new Insets(10));
    gridPane.setHgap(4);
    gridPane.setVgap(8);

    gridPane.add(titleLabel, 0, 0, 2, 1);
    gridPane.add(octavesLabel, 0, 1);
    gridPane.add(octavesTextField, 1, 1);
    gridPane.add(startAmplitudeLabel, 0, 2);
    gridPane.add(startAmplitudeTextField, 1, 2);
    gridPane.add(frequencyDivisorLabel, 0, 3);
    gridPane.add(frequencyDivisorTextField, 1, 3);
    gridPane.add(lacunarityLabel, 0, 4);
    gridPane.add(lacunarityTextField, 1, 4);
    gridPane.add(gainLabel, 0, 5);
    gridPane.add(gainTextField, 1, 5);
    gridPane.add(granularityLabel, 0, 6);
    gridPane.add(granularityTextField, 1, 6);

    return gridPane;
  }

  private void initListeners(
      TextField textField,
      double defaultValue,
      double step,
      DoubleSupplier getter,
      DoubleConsumer setter) {
    TextFormatter<Number> formatter =
        new TextFormatter<>(new NumberStringConverter(), defaultValue);
    textField.setTextFormatter(formatter);
    Runnable updateValue =
        () ->
            Optional.ofNullable(formatter.getValue())
                .ifPresent(
                    x -> {
                      if (getter.getAsDouble() != x.doubleValue()) {
                        setter.accept(x.doubleValue());
                        drawCanvas();
                      }
                    });
    textField.setOnAction(event -> updateValue.run());
    textField
        .focusedProperty()
        .addListener(
            (observable, oldValue, newValue) -> {
              if (!newValue) updateValue.run();
            });
    textField.setOnScroll(
        event -> {
          double value = getter.getAsDouble() + Math.signum(event.getDeltaY()) * step;
          value = Math.round(value * 100000d) / 100000d; // hide rounding errors
          formatter.setValue(value);
          setter.accept(value);
          drawCanvas();
        });
  }

  private void drawCanvas() {
    PixelWriter pixelWriter = canvas.getGraphicsContext2D().getPixelWriter();

    for (int y = 0; y < canvas.getHeight(); y++) {
      for (int x = 0; x < canvas.getWidth(); x++) {
        int zoomedX = (int) (x / zoom * 100);
        int zoomedY = (int) (y / zoom * 100);

        Color color = Color.GREY;

        if (showHeight || showTerrain) {
          float height = heightNoise.getValue(zoomedX, zoomedY);

          if (showTerrain) {
            Color terrainColor;

            if (height < -0.3) {
              terrainColor =
                  Color.DARKBLUE
                      .interpolate(Color.BLUE, (height + 1) / 0.3)
                      .deriveColor(0, 0.75, 1, 1);
            } else {
              terrainColor = Color.GREEN.interpolate(Color.PERU, (height + 0.7) / 1.3);
            }

            color = terrainColor;
          } else if (showHeight) {
            color = Color.BLACK.interpolate(Color.WHITE, (height + 1) / 2);
          }
        }

        if (showHeat) {
          float heat = heatNoise.getValue(zoomedX, zoomedY);
          if (heat != 0) {
            Color heatColor = Color.LIGHTBLUE.interpolate(Color.TOMATO, (heat + 1) / 2).saturate();
            color = color.interpolate(heatColor, 0.5);
          }
        }

        pixelWriter.setColor(x, y, color);
      }
    }
  }
}
