package blocks;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
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
import java.util.stream.LongStream;

public class MapApp extends Application {

  public static void main(String[] args) {
    launch();
  }

  private static final int WIDTH = 640;
  private static final int HEIGHT = 480;

  Canvas canvas;

  Noise heightNoise;
  Noise heatNoise;

  Random heightRandom = new Random();
  Random heatRandom = new Random();

  @Override
  public void start(Stage stage) {
    canvas = new Canvas(WIDTH, HEIGHT);

    heightNoise =
        new Noise(4, 140, 2, 0, LongStream.range(0, 4).map(x -> heightRandom.nextLong()).toArray());
    heatNoise =
        new Noise(4, 140, 1.2, 3, LongStream.range(0, 4).map(x -> heatRandom.nextLong()).toArray());

    GridPane heightGridPane = createNoiseParamUi("Height", heightNoise, heightRandom);
    GridPane heatGridPane = createNoiseParamUi("Heat", heatNoise, heatRandom);

    Scene scene = new Scene(new VBox(new HBox(heightGridPane, heatGridPane), canvas));
    stage.setTitle("Blocks Map");
    stage.setScene(scene);
    stage.show();

    drawCanvas();
  }

  private GridPane createNoiseParamUi(String name, Noise noise, Random random) {
    Label titleLabel = new Label(name + " Noise Params");
    titleLabel.setStyle("-fx-font-weight: bold");
    Label octavesLabel = new Label("Octaves");
    TextField octavesInput = new TextField(Integer.toString(noise.octaves));
    Label frequencyDivisorLabel = new Label("Frequency Divisor");
    TextField frequencyDivisorInput = new TextField(Double.toString(noise.frequencyDivisor));
    Label lacunarityLabel = new Label("Lacunarity");
    TextField lacunarityInput = new TextField(Double.toString(noise.lacunarity));
    Label granularityLabel = new Label("Granularity");
    TextField granularityInput = new TextField(Double.toString(noise.granularity));

    initListeners(
        octavesInput,
        noise.octaves,
        1,
        () -> noise.octaves,
        x -> {
          noise.octaves = (int) x;
          noise.seeds = LongStream.range(0, noise.octaves).map(y -> random.nextLong()).toArray();
        });
    initListeners(
        frequencyDivisorInput,
        noise.frequencyDivisor,
        10,
        () -> noise.frequencyDivisor,
        x -> noise.frequencyDivisor = x);
    initListeners(
        lacunarityInput, noise.lacunarity, 0.2, () -> noise.lacunarity, x -> noise.lacunarity = x);
    initListeners(
        granularityInput,
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
    gridPane.add(octavesInput, 1, 1);
    gridPane.add(frequencyDivisorLabel, 0, 2);
    gridPane.add(frequencyDivisorInput, 1, 2);
    gridPane.add(lacunarityLabel, 0, 3);
    gridPane.add(lacunarityInput, 1, 3);
    gridPane.add(granularityLabel, 0, 4);
    gridPane.add(granularityInput, 1, 4);

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
    GraphicsContext context = canvas.getGraphicsContext2D();

    for (int y = 0; y < HEIGHT; y++) {
      for (int x = 0; x < WIDTH; x++) {
        float height = heightNoise.getValue(x, y);
        float heat = heatNoise.getValue(x, y);
        // -1 => black, 0 => grey, +1 => white
        Color heightColor = Color.BLACK.interpolate(Color.WHITE, (height + 1) / 2);
        Color terrainColor;
        if (height < -0.3) {
          terrainColor = Color.BLUE;
        } else if (height < 0.5) {
          terrainColor = Color.GREEN;
        } else {
          terrainColor = Color.PERU;
        }

        Color color = terrainColor.deriveColor(0, 1, 1 + height * 0.5, 1);

        if (heat != 0) {
          Color heatColor = Color.LIGHTBLUE.interpolate(Color.TOMATO, (heat + 1) / 2).saturate();
          color = color.interpolate(heatColor, 0.5);
        }

        context.setFill(color);
        context.fillRect(x, y, 1, 1);
      }
    }
  }
}
