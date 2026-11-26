<p align="center">
    <a href="https://docs.oracle.com/en/java/javase/25/"><img src="https://img.shields.io/badge/Java-Release%2025-green"/></a>
    <span>&nbsp;</span>
    <a href="https://jitpack.io/#io.github.teletha/freja"><img src="https://img.shields.io/jitpack/version/io.github.teletha/freja?label=Repository&color=green"></a>
    <span>&nbsp;</span>
    <a href="https://teletha.github.io/freja"><img src="https://img.shields.io/website.svg?down_color=red&down_message=CLOSE&label=Official%20Site&up_color=green&up_message=OPEN&url=https%3A%2F%2Fteletha.github.io%2Ffreja"></a>
</p>

## Summary
A fast, local-first photo library for the desktop.
<p align="right"><a href="#top">back to top</a></p>






## Editions
Freja ships two interchangeable user interfaces on top of the same SQLite catalog and scanner:

| Edition | Entry point | Toolkit |
|---|---|---|
| Swing (default) | `freja.Freja` | Swing + FlatLaf |
| Browser (Apocrypha) | `freja.web.FrejaWeb` | HTML/CSS/JS via [Apocrypha](https://github.com/teletha/apocrypha) |

All editions read and write the same library (`%LOCALAPPDATA%\Freja\library.db` by default), so
you can switch between them freely. They share the single-instance port, meaning starting one
asks the other to close (disable with `-Dfreja.singleInstance=false` for testing).

To start the browser edition:

```
java --enable-native-access=ALL-UNNAMED -cp <dependencies> freja.web.FrejaWeb --library=<path> [photo folders...]
```

The browser edition (`freja.web.FrejaWeb`, `src/main/resources/web`) uses the same catalog over
IPC. Its grid is virtualized in two layers: only near-viewport rows own DOM card nodes, and photo
metadata is fetched through `freja:browse` in 300-row pages as the user scrolls (section headers
and scroll height are computed from the section counts returned with every reply).

The browser edition is built with Apocrypha: the frontend is HTML/CSS/JS served from the
classpath resource root `web`, and every request and response crosses the `freja:*` IPC channels.
The UI code lives in the `freja.web` package (`FrejaWeb`, `WebHandlers`, `WebModels`,
`PhotoRoutes`, `WebSettings`, `SvelteUI`), and the browser itself is a native webview window with
its own developer tools (`F12`).

The settings page is a Svelte component. Its sources live in `src/main/frontend` and are compiled
through [bun4j](https://github.com/teletha/bun4j) at startup whenever that source tree is present;
the generated JavaScript and CSS are served from `/svelte/`. A packaged application has no source
tree and falls back to the prebuilt assets in `src/main/resources/web/svelte`. Use
`-Dfreja.frontend=<path>` to point the builder at a different frontend directory.


## Prerequisites
Freja runs on all major operating systems and requires only [Java version 25](https://docs.oracle.com/en/java/javase/25/) or later to run.
To check, please run `java -version` on your terminal.
<p align="right"><a href="#top">back to top</a></p>

## Install
For any code snippet below, please substitute the version given with the version of Freja you wish to use.
#### [Maven](https://maven.apache.org/)
Add JitPack repository at the end of repositories element in your build.xml:
```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>
```
Add it into in the dependencies element like so:
```xml
<dependency>
    <groupId>io.github.teletha</groupId>
    <artifactId>freja</artifactId>
    <version>1.0</version>
</dependency>
```
#### [Gradle](https://gradle.org/)
Add JitPack repository at the end of repositories in your build.gradle:
```gradle
repositories {
    maven { url "https://jitpack.io" }
}
```
Add it into the dependencies section like so:
```gradle
dependencies {
    implementation 'io.github.teletha:freja:1.0'
}
```
#### [SBT](https://www.scala-sbt.org/)
Add JitPack repository at the end of resolvers in your build.sbt:
```scala
resolvers += "jitpack" at "https://jitpack.io"
```
Add it into the libraryDependencies section like so:
```scala
libraryDependencies += "io.github.teletha" % "freja" % "1.0"
```
#### [Leiningen](https://leiningen.org/)
Add JitPack repository at the end of repositories in your project().clj:
```clj
:repositories [["jitpack" "https://jitpack.io"]]
```
Add it into the dependencies section like so:
```clj
:dependencies [[io.github.teletha/freja "1.0"]]
```
#### [Bee](https://teletha.github.io/bee)
Add it into your project definition class like so:
```java
require("io.github.teletha", "freja", "1.0");
```
<p align="right"><a href="#top">back to top</a></p>


## Contributing
Contributions are what make the open source community such an amazing place to learn, inspire, and create. Any contributions you make are **greatly appreciated**.
If you have a suggestion that would make this better, please fork the repo and create a pull request. You can also simply open an issue with the tag "enhancement".
Don't forget to give the project a star! Thanks again!

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

The overwhelming majority of changes to this project don't add new features at all. Optimizations, tests, documentation, refactorings -- these are all part of making this product meet the highest standards of code quality and usability.
Contributing improvements in these areas is much easier, and much less of a hassle, than contributing code for new features.

### Bug Reports
If you come across a bug, please file a bug report. Warning us of a bug is possibly the most valuable contribution you can make to Freja.
If you encounter a bug that hasn't already been filed, [please file a report](https://github.com/teletha/freja/issues/new) with an [SSCCE](http://sscce.org/) demonstrating the bug.
If you think something might be a bug, but you're not sure, ask on StackOverflow or on [freja-discuss](https://github.com/teletha/freja/discussions).
<p align="right"><a href="#top">back to top</a></p>


## Dependency
Freja depends on the following products on runtime.
* [BorderlessSceneFX-1.0.0](https://mvnrepository.com/artifact/com.catwithawand/BorderlessSceneFX/1.0.0)
* [altfx-1.6.2](https://mvnrepository.com/artifact/io.github.teletha/altfx/1.6.2)
* [apocrypha-0.1](https://mvnrepository.com/artifact/io.github.teletha/apocrypha/0.1)
* [bun4j-1.0.1](https://mvnrepository.com/artifact/io.github.teletha/bun4j/1.0.1)
* [avaje-webview-0.19](https://mvnrepository.com/artifact/io.avaje.webview/avaje-webview/0.19)
* [avaje-webview-windows-x86_64-0.19](https://mvnrepository.com/artifact/io.avaje.webview/avaje-webview-windows-x86_64/0.19)
* [conjure-1.2.2](https://mvnrepository.com/artifact/io.github.teletha/conjure/1.2.2)
* [controlsfx-11.2.5](https://mvnrepository.com/artifact/org.controlsfx/controlsfx/11.2.5)
* [error_prone_annotations-2.27.0](https://mvnrepository.com/artifact/com.google.errorprone/error_prone_annotations/2.27.0)
* [flatlaf-3.8](https://mvnrepository.com/artifact/com.formdev/flatlaf/3.8)
* [flatlaf-intellij-themes-3.8](https://mvnrepository.com/artifact/com.formdev/flatlaf-intellij-themes/3.8)
* [ikonli-core-12.4.0](https://mvnrepository.com/artifact/org.kordamp.ikonli/ikonli-core/12.4.0)
* [ikonli-fontawesome5-pack-12.4.0](https://mvnrepository.com/artifact/org.kordamp.ikonli/ikonli-fontawesome5-pack/12.4.0)
* [ikonli-javafx-12.4.0](https://mvnrepository.com/artifact/org.kordamp.ikonli/ikonli-javafx/12.4.0)
* [imagify-1.3.1](https://mvnrepository.com/artifact/io.github.teletha/imagify/1.3.1)
* [javafx-base-28-ea+12](https://mvnrepository.com/artifact/org.openjfx/javafx-base/28-ea+12)
* [javafx-base-28-ea+12-linux](https://mvnrepository.com/artifact/org.openjfx/javafx-base/28-ea+12)
* [javafx-controls-28-ea+12](https://mvnrepository.com/artifact/org.openjfx/javafx-controls/28-ea+12)
* [javafx-controls-28-ea+12-linux](https://mvnrepository.com/artifact/org.openjfx/javafx-controls/28-ea+12)
* [javafx-graphics-28-ea+12](https://mvnrepository.com/artifact/org.openjfx/javafx-graphics/28-ea+12)
* [javafx-graphics-28-ea+12-linux](https://mvnrepository.com/artifact/org.openjfx/javafx-graphics/28-ea+12)
* [javafx-media-28-ea+12](https://mvnrepository.com/artifact/org.openjfx/javafx-media/28-ea+12)
* [javafx-media-28-ea+12-linux](https://mvnrepository.com/artifact/org.openjfx/javafx-media/28-ea+12)
* [javafx-web-28-ea+12](https://mvnrepository.com/artifact/org.openjfx/javafx-web/28-ea+12)
* [javafx-web-28-ea+12-linux](https://mvnrepository.com/artifact/org.openjfx/javafx-web/28-ea+12)
* [jna-5.12.1](https://mvnrepository.com/artifact/net.java.dev.jna/jna/5.12.1)
* [jna-platform-5.12.1](https://mvnrepository.com/artifact/net.java.dev.jna/jna-platform/5.12.1)
* [jspecify-1.0.1](https://mvnrepository.com/artifact/org.jspecify/jspecify/1.0.1)
* [jsvg-2.2.0](https://mvnrepository.com/artifact/com.github.weisj/jsvg/2.2.0)
* [lycoris-1.1.1](https://mvnrepository.com/artifact/io.github.teletha/lycoris/1.1.1)
* [miglayout-core-11.5.2](https://mvnrepository.com/artifact/com.miglayout/miglayout-core/11.5.2)
* [miglayout-swing-11.5.2](https://mvnrepository.com/artifact/com.miglayout/miglayout-swing/11.5.2)
* [openjfx-monocle-jdk-12.0.1+2](https://mvnrepository.com/artifact/org.testfx/openjfx-monocle/jdk-12.0.1+2)
* [psychopath-2.3.0](https://mvnrepository.com/artifact/io.github.teletha/psychopath/2.3.0)
* [sinobu-4.16.0](https://mvnrepository.com/artifact/io.github.teletha/sinobu/4.16.0)
* [slf4j-api-2.1.0-alpha1](https://mvnrepository.com/artifact/org.slf4j/slf4j-api/2.1.0-alpha1)
* [sprouts-2.8.1](https://mvnrepository.com/artifact/io.github.globaltcad/sprouts/2.8.1)
* [sqlite-jdbc-3.54.1.0](https://mvnrepository.com/artifact/org.xerial/sqlite-jdbc/3.54.1.0)
* [stylist-1.16.1](https://mvnrepository.com/artifact/io.github.teletha/stylist/1.16.1)
* [swing-tree-1.1.2](https://mvnrepository.com/artifact/io.github.globaltcad/swing-tree/1.1.2)
<p align="right"><a href="#top">back to top</a></p>


## License
Copyright (C) 2026 The FREJA Development Team

MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
<p align="right"><a href="#top">back to top</a></p>
