/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import kiss.Disposable;
import kiss.I;
import stylist.Style;
import stylist.StyleDSL;
import freja.model.Photo;
import viewtify.Viewtify;
import viewtify.ui.UIButton;
import viewtify.ui.UILabel;
import viewtify.ui.View;
import viewtify.ui.ViewDSL;
import viewtify.ui.helper.User;

/**
 * The full-screen viewer: zoom, pan, keyboard navigation and slideshow, opened as a top-most
 * window over the screen of the owner instead of an exclusive display mode. The Viewtify
 * counterpart of the Swing {@code PhotoViewer}.
 */
public final class PhotoViewerView extends View {

    /** Receives changes made while the viewer is open. */
    public interface Listener {

        void onFavorite(Photo photo, boolean favorite);
    }

    /** Slideshow interval. */
    private static final int SLIDESHOW_MILLIS = 3600;

    private final StackPane canvas = new StackPane();

    private final ImageView picture = new ImageView();

    private final AtomicLong generation = new AtomicLong();

    private final ExecutorService loader = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "freja-fx-viewer");
        thread.setDaemon(true);
        return thread;
    });

    private final List<Photo> photos;

    private final Listener listener;

    private UILabel title;

    private UILabel counter;

    private UIButton previous;

    private UIButton next;

    private UIButton favorite;

    private UIButton fitButton;

    private UIButton slideshow;

    private UIButton close;

    private Stage stage;

    private Disposable slideshowTimer;

    private boolean playing;

    private int index;

    private Photo photo;

    private boolean fitMode = true;

    private double zoom = 1;

    private double panX;

    private double panY;

    private double dragX;

    private double dragY;

    /** Opens a viewer covering the screen of the owner. */
    public static PhotoViewerView open(Stage owner, List<Photo> photos, Photo start, Listener listener, Image placeholder) {
        PhotoViewerView view = new PhotoViewerView(photos, start, listener);
        // Showing the small catalog thumbnail here made it flash in the centre before the full
        // image arrived. Keep the canvas empty with a loading note until the real image is ready.
        view.showStatus("読み込み中…");
        view.present(owner);
        return view;
    }

    private PhotoViewerView(List<Photo> photos, Photo start, Listener listener) {
        this.photos = new ArrayList<>(photos.isEmpty() ? List.of(start) : photos);
        this.index = Math.max(0, this.photos.indexOf(start));
        this.listener = listener == null ? (photo, favorite) -> {
        } : listener;
    }

    /** Builds the window after the UI tree exists. */
    private void present(Stage owner) {
        stage = new Stage(StageStyle.UNDECORATED);
        if (owner != null) {
            try {
                stage.initOwner(owner);
            } catch (IllegalStateException ignored) {
                // The owner is only a courtesy; showing without it is fine.
            }
        }
        Scene scene = new Scene((javafx.scene.Parent) ui(), 1280, 800);
        // Without this the Stage still opens and grabs input (it is undecorated, full-screen and
        // always-on-top) but has nothing to paint: an invisible window blocking the whole display.
        // Viewtify.manage also needs the scene attached so it can find the owning Stage.
        stage.setScene(scene);
        scene.setFill(javafx.scene.paint.Color.web("#0D0E10"));
        Viewtify.manage("viewer", scene, true);
        Node root = scene.getRoot();
        root.setFocusTraversable(true);

        // Cover the screen the owner lives on, like the Swing edition.
        Screen screen = Screen.getPrimary();
        if (owner != null) {
            List<Screen> candidates = Screen.getScreensForRectangle(owner.getX(), owner.getY(), 1, 1);
            if (!candidates.isEmpty()) {
                screen = candidates.get(0);
            }
        }
        // Position only: JavaFX decides the actual full-screen size in logical pixels. Using
        // Screen bounds for the size double-counts HiDPI scaling and made the image ~2x too large.
        Rectangle2D bounds = screen.getVisualBounds();
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setAlwaysOnTop(true);
        stage.setFullScreenExitHint("");
        stage.setFullScreenExitKeyCombination(javafx.scene.input.KeyCombination.NO_MATCH);
        stage.setOnShown(event -> root.requestFocus());
        stage.setOnHidden(event -> shutdown());

        installInput(scene);
        showPhoto(index);
        stage.show();
        stage.setFullScreen(true);
        // The canvas had no size while the scene was built, so the fit scale computed from the
        // thumbnail placeholder was wrong. Recompute once the window has been laid out and on
        // every later resize (including entering/leaving full screen).
        javafx.application.Platform.runLater(() -> {
            applyTransform();
            root.requestFocus();
        });
    }

    private void installInput(Scene scene) {
        scene.setOnKeyPressed(event -> {
            KeyCode code = event.getCode();
            if (code == KeyCode.LEFT) {
                move(-1);
            } else if (code == KeyCode.RIGHT) {
                move(1);
            } else if (code == KeyCode.ESCAPE) {
                close();
            } else if (code == KeyCode.SPACE) {
                toggleSlideshow();
            } else if (code == KeyCode.PLUS || code == KeyCode.ADD) {
                zoomBy(1.18);
            } else if (code == KeyCode.MINUS || code == KeyCode.SUBTRACT) {
                zoomBy(1 / 1.18);
            } else if (code == KeyCode.DIGIT0 || code == KeyCode.NUMPAD0 || code == KeyCode.F) {
                setFit();
            }
        });
        // Wheel up goes to the previous photo, wheel down to the next one.
        canvas.setOnScroll(event -> {
            if (event.getDeltaY() != 0) {
                move(event.getDeltaY() > 0 ? -1 : 1);
            }
        });
        canvas.setOnMousePressed(event -> {
            dragX = event.getX();
            dragY = event.getY();
            if (event.getClickCount() == 2) {
                close();
            }
        });
        canvas.setOnMouseDragged(event -> {
            if (!fitMode) {
                panX += event.getX() - dragX;
                panY += event.getY() - dragY;
                dragX = event.getX();
                dragY = event.getY();
                applyTransform();
            }
        });
        canvas.setOnMouseMoved(event -> canvas.setCursor(fitMode ? Cursor.DEFAULT : Cursor.OPEN_HAND));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ViewDSL declareUI() {
        return new ViewDSL() {
            {
                $(vbox, style.root, () -> {
                    $(hbox, style.toolbar, () -> {
                        $(vbox, style.caption, () -> {
                            $(title, style.title);
                            $(counter, style.counter);
                        });
                        $(ui(grow()));
                        $(previous);
                        $(next);
                        $(favorite);
                        $(fitButton);
                        $(slideshow);
                        $(close);
                    });
                    $(ui(canvas), style.canvas);
                    $(hbox, style.footer, () -> {
                        $(ui(help.ui()));
                    });
                });
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void initialize() {
        title.text(" ");
        counter.text(" ");
        previous.text("‹").tooltip(en("Previous"));
        next.text("›").tooltip(en("Next"));
        favorite.text("☆").tooltip(en("Favorite"));
        fitButton.text(en("Fit")).tooltip(en("Show the whole image"));
        slideshow.text("▶").tooltip(en("Slideshow"));
        close.text("✕").tooltip(en("Close"));
        for (UIButton button : new UIButton[] {previous, next, favorite, fitButton, slideshow, close}) {
            button.style(style.tool);
            button.focusable(false);
        }
        picture.setPreserveRatio(true);
        picture.setSmooth(true);
        // The picture is positioned explicitly in applyTransform() so centering does not depend on
        // how StackPane combines the image's layout bounds with a scale transform.
        picture.setManaged(false);
        // Without vgrow the canvas keeps its preferred height (~the placeholder image) and the fit
        // scale is computed from a tiny strip instead of the whole screen.
        javafx.scene.layout.VBox.setVgrow(canvas, Priority.ALWAYS);
        canvas.setMinHeight(0);
        canvas.widthProperty().addListener((o, old, value) -> applyTransform());
        canvas.heightProperty().addListener((o, old, value) -> applyTransform());
        canvas.setAlignment(Pos.CENTER);
        canvas.getChildren().add(picture);
        status.style(style.loading);
        status.ui().setMaxSize(900, 100);
        canvas.getChildren().add(status.ui());
        help.text("← →  またはスクロールで移動   ·   ドラッグで移動   ·   + / - でズーム   ·   0 または F でフィット   ·   Space でスライドショー   ·   Esc で閉じる");
        help.style(style.counter);
        previous.when(User.Action, event -> move(-1));
        next.when(User.Action, event -> move(1));
        favorite.when(User.Action, event -> toggleFavorite());
        fitButton.when(User.Action, event -> setFit());
        slideshow.when(User.Action, event -> toggleSlideshow());
        close.when(User.Action, event -> close());
    }

    /** The keyboard hints, shown in the footer. */
    private final UILabel help = new UILabel(null);

    /** The loading or error message, shown over the canvas. */
    private final UILabel status = new UILabel(null);

    // ----------------------------------------------------------------------------
    // Navigation
    // ----------------------------------------------------------------------------

    private void move(int delta) {
        if (photos.isEmpty()) {
            return;
        }
        showPhoto(Math.floorMod(index + delta, photos.size()));
    }

    private void showPhoto(int next) {
        if (photos.isEmpty() || next < 0 || next >= photos.size()) {
            return;
        }
        index = next;
        photo = photos.get(index);
        fitMode = true;
        panX = 0;
        panY = 0;
        updateToolbar();
        applyTransform();
        long token = generation.incrementAndGet();
        Photo target = photo;
        loader.execute(() -> {
            Image image = null;
            String failure = null;
            try {
                image = FxImages.full(target);
            } catch (Throwable e) {
                failure = e.getMessage() == null ? "画像を読み込めません" : e.getMessage();
            }
            Image result = image;
            String message = failure;
            Viewtify.inUI(() -> {
                if (token != generation.get()) {
                    return;
                }
                if (result != null) {
                    showImage(result);
                } else {
                    showStatus(message == null ? "画像を読み込めません" : "画像を読み込めません: " + message);
                }
            });
        });
    }

    private void showImage(Image image) {
        picture.setImage(image);
        status.visible(false).managed(false);
        applyTransform();
    }

    /** Replaces the picture area with a centered status line. */
    private void showStatus(String text) {
        status.text(text).visible(true);
        status.ui().toFront();
    }

    private void updateToolbar() {
        if (photo == null) {
            title.text(" ");
            counter.text(" ");
            return;
        }
        title.text(photo.fileName());
        counter.text((index + 1) + " / " + photos.size() + "   ·   " + photo.dimensionsLabel() + "   ·   " + photo
                .sizeLabel());
        favorite.text(photo.favorite() ? "★" : "☆");
        favorite.ui().setStyle("-fx-text-fill: " + (photo.favorite() ? "#F1B64A" : "#C8CDD4") + ";");
        favorite.tooltip(photo.favorite() ? en("Remove from favorites") : en("Add to favorites"));
    }

    private void toggleFavorite() {
        if (photo == null) {
            return;
        }
        photo = photo.withFavorite(!photo.favorite());
        photos.set(index, photo);
        updateToolbar();
        listener.onFavorite(photo, photo.favorite());
    }

    private void toggleSlideshow() {
        if (!playing) {
            playing = true;
            slideshowTimer = I.schedule(0, SLIDESHOW_MILLIS, TimeUnit.MILLISECONDS, false).to(tick -> move(1));
            slideshow.text("❚❚");
            slideshow.tooltip(en("Stop the slideshow"));
        } else {
            stopSlideshow();
        }
    }

    private void stopSlideshow() {
        playing = false;
        if (slideshowTimer != null) {
            slideshowTimer.dispose();
            slideshowTimer = null;
        }
        slideshow.text("▶");
        slideshow.tooltip(en("Slideshow"));
    }

    /** Starts the slideshow unless it already runs. */
    public void startSlideshow() {
        if (!playing) {
            toggleSlideshow();
        }
    }

    private void setFit() {
        fitMode = true;
        panX = 0;
        panY = 0;
        zoom = 1;
        applyTransform();
    }

    private void zoomBy(double factor) {
        if (picture.getImage() == null) {
            return;
        }
        fitMode = false;
        zoom = Math.max(0.05, Math.min(8, (fitMode ? 1 : zoom) * factor));
        applyTransform();
    }

    private double fitScale() {
        Image image = picture.getImage();
        if (image == null || image.getWidth() <= 0) {
            return 1;
        }
        double width = Math.max(100, canvas.getWidth() - 80);
        double height = Math.max(100, canvas.getHeight() - 60);
        return Math.min(2.5, Math.min(width / image.getWidth(), height / image.getHeight()));
    }

    private void applyTransform() {
        if (fitMode) {
            zoom = fitScale();
        }
        Image image = picture.getImage();
        double width = image == null ? 0 : image.getWidth();
        double height = image == null ? 0 : image.getHeight();
        // JavaFX scales around the node's centre, so positioning the unscaled image at the centre
        // keeps the scaled image centred too (for both portrait and landscape).
        picture.setLayoutX((canvas.getWidth() - width) / 2);
        picture.setLayoutY((canvas.getHeight() - height) / 2);
        picture.setScaleX(zoom);
        picture.setScaleY(zoom);
        picture.setTranslateX(panX);
        picture.setTranslateY(panY);
    }

    /** Closes the viewer window. */
    public void close() {
        if (stage != null) {
            stage.close();
        } else {
            shutdown();
        }
    }

    /** Stops every background activity of this viewer. */
    private void shutdown() {
        generation.incrementAndGet();
        stopSlideshow();
        loader.shutdownNow();
        Viewtify.unmanage("viewer");
    }

    /** A horizontal spacer which eats the remaining width. */
    private static Region grow() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    /** Visual language of the viewer. */
    interface style extends StyleDSL {

        Style root = () -> background.color("#0D0E10");

        Style toolbar = () -> {
            background.color("#1A1C20");
            padding.vertical(10, px).horizontal(16, px);
        };

        Style caption = () -> {
        };

        Style title = () -> font.size(14, px).weight(700).color("#FFFFFF");

        Style counter = () -> font.size(11, px).color("#9AA0A8");

        Style tool = () -> {
            background.color("transparent");
            border.none();
            font.size(14, px).color("#D7DADF");
            padding.vertical(5, px).horizontal(10, px);
            cursor.pointer();
            $.hover(() -> background.color("rgba(255,255,255,0.08)"));
        };

        Style canvas = () -> background.color("#0D0E10");

        Style footer = () -> {
            background.color("#1A1C20");
            padding.vertical(7, px).horizontal(16, px);
        };

        Style loading = () -> font.size(13, px).color("#C9CDD3");
    }
}
