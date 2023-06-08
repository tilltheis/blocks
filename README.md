# Blocks

A Minecraft-style world generator.

## Running

The app has only been tested on Windows 10 and 11, using Java 17 and Maven.

jMonkeyEngine doesn't properly support macOS.
Maybe the app works on Linux.

```shell
mvn compile exec:java
```

## Ideas

* place random paths that lead through the map, building bridges across rivers, tunnels through mountains, etc

## Map App

A Simplex noise visualizer.

It needs to be run with the following JVM options, assuming you're running it on Windows:

```shell
--module-path "C:\Users\<your-username>\.m2\repository\org\openjfx\javafx-base\19\javafx-base-19-win.jar";"C:\Users\<your-username>\.m2\repository\org\openjfx\javafx-controls\19\javafx-controls-19-win.jar";"C:\Users\<your-username>\.m2\repository\org\openjfx\javafx-graphics\19\javafx-graphics-19-win.jar"
--add-modules=javafx.base,javafx.controls,javafx.graphics
```