<p align="center">
    <a href="https://docs.oracle.com/en/java/javase/25/"><img src="https://img.shields.io/badge/Java-Release%2025-green"/></a>
    <span>&nbsp;</span>
    <a href="https://jitpack.io/#io.github.teletha/freja"><img src="https://img.shields.io/jitpack/version/io.github.teletha/freja?label=Repository&color=green"></a>
    <span>&nbsp;</span>
    <a href="https://teletha.github.io/freja"><img src="https://img.shields.io/website.svg?down_color=red&down_message=CLOSE&label=Official%20Site&up_color=green&up_message=OPEN&url=https%3A%2F%2Fteletha.github.io%2Ffreja"></a>
</p>







Freja is a fast, local-first photo library for the desktop, inspired by Picasa.
It watches the folders you choose, generates thumbnails and metadata in the background, and
stores the catalog in SQLite. Your original files are never moved or modified.

## Features

* **Folder library** — register any number of directories and browse them from a compact sidebar.
  Every subdirectory is registered as a folder of its own, with its own counts, display order and
  continuous scanning. Adding `A` therefore shows `A`, `A/child1`, `A/child2` and so on.
* **One-time and continuous scanning** — a full scan on startup, a `WatchService` for new,
  changed and deleted files, and a periodic sweep for anything the platform watcher missed.
* **SQLite catalog** — photos, folders, user ratings, favorites, captions and thumbnail blobs live
  in one portable database (`WAL` mode).
* **Thumbnails and metadata** — decoded once by [imagify](https://github.com/teletha/imagify) and
  stored as compact WebP thumbnails (maximum edge 320 px, quality 0.6) with transparency preserved.
  Downscaling uses imagify's **Catmull-Rom (CATROM)** resampler.
  JPEG EXIF camera, lens, capture date, exposure, aperture, ISO,
  focal length and orientation are read without extra dependencies.
* **Picasa-like browsing** — timeline groups, virtualized thumbnail grid, zoom slider,
  filename/folder/camera/caption search, favorites, recent photos and star ratings. The library
  view is one continuous list of folder sections in sidebar order, regardless of the real directory
  hierarchy; selecting a folder scrolls to its rich, sticky header. Nearby jumps animate smoothly,
  while long jumps land immediately. The grid only loads the thumbnails that are visible.
* **Per-folder display order** — choose name order or date order for each folder independently.
  Name order is the default, and the choice is stored in SQLite with the folder.
* **Full screen viewer** — keyboard navigation, zoom, pan, slideshow, and favorite toggling.
* **Non-destructive** — removing a folder or photo from the library never deletes the file on
  disk.
* **Light and dark themes** — switch instantly from the toolbar or the View menu. The choice is
  remembered between launches.
* **Japanese desktop UI** built with Swing and [FlatLaf](https://www.formdev.com/flatlaf/).

## Run

Build an executable uber-jar:

```shell
bee jar:merge
```

Then open the application, optionally passing folders to register immediately:

```shell
java -jar target/freja-1.0.jar
java -jar target/freja-1.0.jar D:\Pictures D:\Camera
```

The catalog defaults to:

* Windows: `%LOCALAPPDATA%\Freja\library.db`
* macOS / Linux: `~/.freja/library.db`

Use `--library=<path>` to keep a catalog somewhere else.

```shell
java -jar target/freja-1.0.jar --library=D:\FrejaTest\library.db D:\Download\test
```

Supported formats are JPEG, PNG, GIF, WebP, AVIF and BMP. Animated images are shown by their first
frame. Files with damaged or unsupported image data remain in the catalog with a placeholder so
they can be retried from the context menu.

## Architecture

| Package | Responsibility |
| --- | --- |
| `freja.store` | SQLite schema and queries, EXIF parsing, thumbnail generation |
| `freja.scan` | initial and incremental scans, `WatchService`, debounce, scan progress |
| `freja.model` | immutable photo, folder, filter and statistics records |
| `freja.ui` | main window, virtualized grid, inspector, viewer and theme |
| `freja.util` | path normalization and pixel transforms |

The catalog has four tables: `folders`, `photos`, `thumbnails` and `settings`. A user-added folder
is an explicit scan root, while every subdirectory discovered below it is stored as its own managed
folder. Each photo belongs to the directory that directly contains it, so parent and child folders
show their own counts, display order and continuous updates. Scanning compares a file's size and
modification time before decoding it, so a repeat scan of an unchanged library only walks the
directory tree. A scan token removes rows whose files disappeared while keeping user properties
such as favorites and captions intact.

## Development

```shell
bee test          # unit and integration tests
bee compile       # compile the application
bee eclipse       # regenerate the Eclipse classpath after dependency changes
bee jar:merge     # build target/freja-1.0.jar
```

The tests cover EXIF parsing, catalog queries and pruning, metadata extraction and the file watcher.

When launching from an IDE, run `bee eclipse` and refresh the project after changing dependencies.
Otherwise the IDE may use an older classpath which does not contain the SQLite JDBC driver.

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
* [imagify](https://github.com/teletha/imagify) for image decoding and format support
* [FlatLaf](https://www.formdev.com/flatlaf/) for the Swing look and feel
* [sqlite-jdbc](https://github.com/xerial/sqlite-jdbc) for the local catalog
* [sinobu](https://github.com/teletha/sinobu) and [psychopath](https://github.com/teletha/psychopath) as the project foundation
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
