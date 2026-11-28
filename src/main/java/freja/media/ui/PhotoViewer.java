/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.media.ui;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import freja.mpv.Mpv;
import freja.mpv.MpvNative;
import freja.mpv.Win32Window;
import freja.Icons;
import freja.Theme;
import freja.media.Photo;
import freja.media.store.Archives;
import freja.util.ImageTransforms;
import imagify.Imagify;
/**
 * A full-screen viewer with a video-site style overlay. The controls live in a separate translucent
 * window, so fading them in or out never changes the size of the photo or video underneath.
 */
public final class PhotoViewer extends JDialog {
    /** Receives changes made while the viewer is open. */
    public interface Listener {
        void onFavorite(Photo photo, boolean favorite);
        /** Called when the viewer closes, with the photo that was on screen last. */
        default void onClose(Photo photo) {
        }
        /** Returns where a video was last left, in milliseconds. Called off the UI thread. */
        default long playbackPosition(long photoId) {
            return 0;
        }
        /** Stores the last playback position, in milliseconds. Called off the UI thread. */
        default void onPlaybackPosition(long photoId, long millis) {
        }
    }
    private static final boolean TRANSLUCENT = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getDefaultScreenDevice()
            .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT);
    private final List<Photo> photos;
    private int index;
    private final Canvas canvas = new Canvas();
    /** Heavyweight surface libmpv renders into; invisible for still images. */
    private final java.awt.Canvas videoCanvas = new java.awt.Canvas();
    private final String windowTitle = "Freja Viewer " + Long.toHexString(System.nanoTime());
    private Mpv player;
    /** Identifies the video whose position is being played, not the photo currently on screen. */
    private long playingPhotoId = -1;
    private boolean videoStarting;
    private final JButton play = control(Icons.play(15, Color.WHITE), "再生");
    private final JButton prevSection = control(Icons.chevron(15, Color.WHITE, true), "前の項目");
    private final JButton nextSection = control(Icons.chevron(15, Color.WHITE, false), "次の項目");
    private final JButton rewind10 = control(textIcon("-10", Color.WHITE), "10秒戻る");
    private final JButton forward10 = control(textIcon("+10", Color.WHITE), "10秒進む");
    private final JButton prevChapter = control(textIcon("|<", Color.WHITE), "前のチャプター");
    private final JButton nextChapter = control(textIcon(">|", Color.WHITE), "次のチャプター");
    private final JButton mute = control(speaker(false), "ミュート");
    private final JButton subtitle = control(textIcon("CC", Color.WHITE), "字幕の表示");
    private final JButton speed = control(textIcon("1.0x", Color.WHITE), "再生速度");
    private final JPopupMenu speedMenu = new JPopupMenu();
    private final Slider volume = new Slider(this::applyVolume, () -> {
    });
    private final Slider seek = new Slider(this::previewSeek, this::commitSeek);
    /** Chapters of the loaded video, used for the seek bar marks and chapter navigation. */
    private List<Mpv.Chapter> chapters = List.of();
    private final JLabel videoTitle = new JLabel();
    private final JLabel time = new JLabel("00:00 / 00:00");
    private final JButton slideshowPlay = button(Icons.play(15, Color.WHITE), "スライドショー再生");
    private final JButton slideshowStop = button(null, "スライドショー停止");
    private final JLabel metadata = new JLabel();
    private final JPanel imageBottom = new JPanel(new BorderLayout(16, 0));
    /** A full-width transport strip under the video. */
    private final JPanel videoControls = new JPanel(new BorderLayout(0, 6));
    private JWindow controlsWindow;
    private final JPanel controlsRoot = new JPanel(new BorderLayout());
    /**
     * A glass pane which only claims hits inside the seek bar. The bar itself stays transparent so
     * the video shows through; this catches clicks in its full height and lets every other click
     * reach the picture underneath.
     */
    private final JPanel seekCatcher = new JPanel() {
        @Override
        public boolean contains(int x, int y) {
            if (!controlsWindow.isVisible() || !videoControls.isVisible()) {
                return false;
            }
            Point screen = getLocationOnScreen();
            screen.translate(x, y);
            return seekScreenBounds().contains(screen);
        }
    };
    private final JLabel toast = new JLabel("", SwingConstants.CENTER) {
        @Override
        protected void paintComponent(Graphics graphics) {
            if (!isVisible() || getText().isEmpty()) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(0, 0, 0, 175));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g.setColor(new Color(255, 255, 255, 70));
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    };
    private final Timer toastTimer = new Timer(1200, event -> toast.setVisible(false));
    private final JPanel helpPanel = new JPanel(new BorderLayout());
    private JPanel topPanel;
    private JPanel bottomPanel;
    private final JPanel toastHost = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)) {
        @Override
        public boolean contains(int x, int y) {
            return false;
        }
    };
    private boolean helpVisible;
    private boolean chromeVisible = true;
    private boolean hoveringControls;
    private float overlayAlpha = 1;
    private boolean fadingIn = true;
    /** Last pointer position that revealed the overlay; event floods must not extend the idle timer. */
    private Point lastPointer;
    /** Throttles the fast keyframe seeks issued while the seek bar is being dragged. */
    private long lastScrubNanos;
    /** Fades the overlay out four seconds after the pointer stops moving. */
    private final Timer chromeTimer = new Timer(4000, event -> hideOverlayIfIdle());
    /** Delays a single click so a double click can close the viewer instead. */
    private final Timer clickTimer = new Timer(220, event -> togglePause());
    {
        clickTimer.setRepeats(false);
    }
    /** Advances the fade in small steps so the media underneath never resizes. */
    private final Timer fadeTimer = new Timer(25, event -> advanceFade());
    private final Timer progress = new Timer(16, event -> updateProgress());
    private final ExecutorService loader = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "freja-viewer");
        thread.setDaemon(true);
        return thread;
    });
    /** Position saves outlive the decode queue so closing the viewer cannot drop the last one. */
    private final ExecutorService saves = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "freja-viewer-save");
        thread.setDaemon(true);
        return thread;
    });
    /** How long a failed or very slow decode holds the window back before it is shown anyway. */
    private static final int REVEAL_DELAY_MS = Integer.getInteger("freja.viewer.revealDelayMs", 1500);
    private final AtomicLong generation = new AtomicLong();
    private final Timer slideshowTimer;
    /** Shows the window once the first decode is in place, or after a short safety delay. */
    private final Timer revealTimer;
    /** Whether the window has been made visible yet. */
    private boolean revealed;
    /** Set by {@link #dispose()}, so a pending reveal never shows a closed window. */
    private boolean closed;
    private Photo photo;
    private BufferedImage image;
    private long loadedPhotoId = -1;
    private String error;
    /**
     * Supplies the stored placeholder thumbnail for a photo whose original cannot be decoded.
     * The catalog keeps a generated image for those rows, so the viewer can still show something.
     */
    private Function<Photo, BufferedImage> placeholderProvider = photo -> null;
    /** Photos whose original failed to decode, so navigation does not re-read the same file. */
    private final Set<Long> unreadable = ConcurrentHashMap.newKeySet();
    /** Decoded placeholders, keyed by photo id, so revisiting a broken photo costs nothing. */
    private final Map<Long, BufferedImage> placeholders = new ConcurrentHashMap<>();
    /** Photos for which the provider had no placeholder; avoids asking again. */
    private final Set<Long> missingPlaceholders = ConcurrentHashMap.newKeySet();
    private boolean fit = true;
    private double zoom = 1;
    private Point dragStart;
    private double panX;
    private double panY;
    private double panStartX;
    private double panStartY;
    private long lastWheelMove;
    private Listener listener = (photo, favorite) -> {
    };
    public PhotoViewer(Window owner, List<Photo> photos, Photo start) {
        super(owner, "Freja", ModalityType.MODELESS);
        // A unique title lets Win32Window find this dialog without reflecting into AWT internals.
        setTitle(windowTitle);
        setIconImages(Icons.applicationImages());
        this.photos = List.copyOf(photos);
        this.index = Math.max(0, this.photos.indexOf(start));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setUndecorated(true);
        setBackground(Theme.VIEWER);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Theme.VIEWER);
        JLayeredPane stage = new JLayeredPane();
        stage.add(canvas, JLayeredPane.DEFAULT_LAYER);
        stage.add(videoCanvas, JLayeredPane.PALETTE_LAYER);
        videoCanvas.setVisible(false);
        stage.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                canvas.setBounds(0, 0, stage.getWidth(), stage.getHeight());
                videoCanvas.setBounds(0, 0, stage.getWidth(), stage.getHeight());
                positionControls();
            }
        });
        add(stage, BorderLayout.CENTER);
        slideshowTimer = new Timer(3600, event -> move(1));
        slideshowTimer.setInitialDelay(3600);
        revealTimer = new Timer(REVEAL_DELAY_MS, event -> reveal());
        revealTimer.setRepeats(false);
        buildOverlay();
        installSeekCatcher();
        // When the overlay is hidden (or reduced to the close button for videos) the viewer itself
        // still sees pointer movement and can bring it back.
        getRootPane().addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                pointerMoved(event);
            }
            @Override
            public void mouseDragged(MouseEvent event) {
                pointerMoved(event);
            }
        });
        installKeys();
        installCanvasInput();
        showPhoto(index);
        chromeTimer.restart();
    }
    /** Builds the single translucent window which carries every control. */
    private void buildOverlay() {
        controlsRoot.setOpaque(false);
        controlsRoot.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        JButton close = circular(Icons.close(17, Color.WHITE), "閉じる");
        close.addActionListener(event -> dispose());
        topPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        topPanel.setOpaque(false);
        topPanel.add(close);
        controlsRoot.add(topPanel, BorderLayout.NORTH);
        // Images: slideshow controls on the left, name / size / date centred at the bottom.
        slideshowStop.setIcon(filledSquare(11));
        slideshowStop.setToolTipText("スライドショー停止");
        slideshowPlay.addActionListener(event -> startSlideshow());
        slideshowStop.addActionListener(event -> {
            slideshowTimer.stop();
            updateSlideshowButtons(false);
        });
        JPanel slideshow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        slideshow.setOpaque(false);
        slideshow.add(slideshowPlay);
        slideshow.add(slideshowStop);
        metadata.setForeground(Color.WHITE);
        metadata.setFont(Theme.font(Font.PLAIN, 12));
        metadata.setHorizontalAlignment(JLabel.CENTER);
        imageBottom.setOpaque(true);
        imageBottom.setBackground(new Color(0, 0, 0, 120));
        imageBottom.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        imageBottom.add(slideshow, BorderLayout.WEST);
        imageBottom.add(metadata, BorderLayout.CENTER);
        // Videos: a full-width seek bar, then one row with the times beside the volume, the
        // transport buttons in the middle and the file name at the bottom right.
        videoControls.setOpaque(false);
        videoControls.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        videoControls.setPreferredSize(new Dimension(0, 80));
        videoTitle.setForeground(Color.WHITE);
        videoTitle.setFont(Theme.font(Font.PLAIN, 12));
        videoTitle.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        time.setForeground(Color.WHITE);
        time.setFont(Theme.font(Font.PLAIN, 13));
        time.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        seek.setRange(0, 1000);
        // Taller than the painted bar so the pointer does not have to land on a 4px line.
        seek.setPreferredSize(new Dimension(600, 48));
        volume.setRange(0, 100);
        volume.value(100);
        volume.setPreferredSize(new Dimension(110, 18));
        JPanel rowLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        rowLeft.setOpaque(false);
        rowLeft.add(subtitle);
        rowLeft.add(mute);
        rowLeft.add(volume);
        rowLeft.add(time);
        JPanel rowCentre = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        rowCentre.setOpaque(false);
        rowCentre.add(prevSection);
        rowCentre.add(prevChapter);
        rowCentre.add(rewind10);
        rowCentre.add(play);
        rowCentre.add(speed);
        rowCentre.add(forward10);
        rowCentre.add(nextChapter);
        rowCentre.add(nextSection);
        JPanel rowRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rowRight.setOpaque(false);
        rowRight.add(videoTitle);
        JPanel buttons = new JPanel(new BorderLayout());
        buttons.setOpaque(false);
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 18));
        buttons.add(rowLeft, BorderLayout.WEST);
        buttons.add(rowCentre, BorderLayout.CENTER);
        buttons.add(rowRight, BorderLayout.EAST);
        JPanel stack = new JPanel(new BorderLayout(0, 0));
        stack.setOpaque(false);
        stack.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 0));
        stack.add(seek, BorderLayout.NORTH);
        stack.add(buttons, BorderLayout.SOUTH);
        videoControls.add(stack, BorderLayout.CENTER);
        play.addActionListener(event -> togglePause());
        mute.addActionListener(event -> toggleMute());
        subtitle.addActionListener(event -> toggleSubtitle());
        speed.addActionListener(event -> speedMenu.show(speed, 0, -speedMenu.getPreferredSize().height));
        for (int step = 1; step <= 8; step++) {
            double value = step * 0.25;
            javax.swing.JMenuItem item = new javax.swing.JMenuItem(speedLabel(value) + "x");
            item.addActionListener(event -> setSpeed(value));
            speedMenu.add(item);
        }
        prevSection.addActionListener(event -> move(-1));
        nextSection.addActionListener(event -> move(1));
        rewind10.addActionListener(event -> jump(-10));
        forward10.addActionListener(event -> jump(10));
        prevChapter.addActionListener(event -> jumpChapter(-1));
        nextChapter.addActionListener(event -> jumpChapter(1));
        bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        bottomPanel.add(imageBottom, BorderLayout.CENTER);
        bottomPanel.add(videoControls, BorderLayout.SOUTH);
        controlsRoot.add(bottomPanel, BorderLayout.SOUTH);
        toast.setForeground(Color.WHITE);
        toast.setFont(Theme.font(Font.BOLD, 22));
        toast.setOpaque(false);
        toast.setEnabled(false);
        toast.setVisible(false);
        toast.setBorder(BorderFactory.createEmptyBorder(10, 22, 10, 22));
        toastHost.setOpaque(false);
        toastHost.add(toast);
        helpPanel.setOpaque(true);
        helpPanel.setBackground(new Color(32, 32, 32));
        helpPanel.setBorder(BorderFactory.createEmptyBorder(24, 24, 12, 24));
        JEditorPane helpText = new JEditorPane("text/html", helpHtml());
        helpText.setEditable(false);
        helpText.setOpaque(true);
        helpText.setBackground(new Color(32, 32, 32));
        helpText.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        JScrollPane helpScroll = new JScrollPane(helpText);
        helpScroll.setBorder(BorderFactory.createEmptyBorder());
        helpScroll.getViewport().setOpaque(false);
        helpPanel.add(helpScroll, BorderLayout.CENTER);
        JButton closeHelp = new JButton("閉じる");
        closeHelp.setForeground(Color.WHITE);
        closeHelp.setFocusPainted(false);
        closeHelp.setContentAreaFilled(false);
        closeHelp.setBorderPainted(false);
        closeHelp.setFont(Theme.font(Font.PLAIN, 12));
        closeHelp.addActionListener(event -> toggleHelp());
        JPanel helpFooter = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        helpFooter.setOpaque(false);
        helpFooter.add(closeHelp);
        helpPanel.add(helpFooter, BorderLayout.SOUTH);
        helpPanel.setVisible(false);
        JPanel centre = new JPanel();
        centre.setLayout(null);
        centre.setOpaque(false);
        centre.add(helpPanel);
        centre.add(toastHost);
        controlsRoot.add(centre, BorderLayout.CENTER);
        videoControls.setVisible(false);
        controlsWindow = new JWindow(this);
        controlsWindow.setFocusableWindowState(false);
        controlsWindow.setBackground(new Color(0, 0, 0, 0));
        controlsWindow.add(controlsRoot);
        controlsWindow.setVisible(false);
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                hoveringControls = true;
                showOverlay();
            }
            @Override
            public void mouseExited(MouseEvent event) {
                hoveringControls = false;
                chromeTimer.restart();
            }
            @Override
            public void mouseMoved(MouseEvent event) {
                showOverlay();
            }
        };
        topPanel.addMouseListener(hover);
        topPanel.addMouseMotionListener(hover);
        imageBottom.addMouseListener(hover);
        imageBottom.addMouseMotionListener(hover);
        videoControls.addMouseListener(hover);
        videoControls.addMouseMotionListener(hover);
        // The empty part of the overlay forwards input to the picture underneath, so panning,
        // double-click and wheel navigation keep working while the controls are on screen.
        controlsRoot.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                relay(event);
            }
            @Override
            public void mouseReleased(MouseEvent event) {
                relay(event);
            }
            @Override
            public void mouseClicked(MouseEvent event) {
                // Playback belongs to the picture: a click on the chrome must not toggle it.
                // Double clicks still close the viewer as they do on the picture.
                if (event.getClickCount() >= 2) {
                    relay(event);
                }
            }
        });
        controlsRoot.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                pointerMoved(event);
            }
            @Override
            public void mouseDragged(MouseEvent event) {
                relay(event);
                pointerMoved(event);
            }
        });
        controlsRoot.addMouseWheelListener(this::relayWheel);
    }
    /** Puts a transparent click catcher over the seek bar without covering the rest of the picture. */
    private void installSeekCatcher() {
        seekCatcher.setOpaque(false);
        setGlassPane(seekCatcher);
        seekCatcher.setVisible(true);
        seekCatcher.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                seek.dragging = true;
                dragSeek(event);
            }
            @Override
            public void mouseReleased(MouseEvent event) {
                if (seek.dragging) {
                    seek.dragging = false;
                    commitSeek();
                }
            }
        });
        seekCatcher.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent event) {
                dragSeek(event);
            }
        });
    }
    private void dragSeek(MouseEvent event) {
        seek.update(event.getLocationOnScreen().x - seek.getLocationOnScreen().x);
    }
    private Rectangle seekScreenBounds() {
        Point origin = seek.getLocationOnScreen();
        Dimension size = seek.getSize();
        return new Rectangle(origin.x, origin.y, size.width, size.height);
    }
    private void installCanvasInput() {
        MouseAdapter screenClick = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                dragStart = event.getPoint();
                panStartX = panX;
                panStartY = panY;
            }
            @Override
            public void mouseReleased(MouseEvent event) {
                dragStart = null;
            }
            @Override
            public void mouseClicked(MouseEvent event) {
                handleScreenClick(event);
            }
        };
        canvas.addMouseListener(screenClick);
        videoCanvas.addMouseListener(screenClick);
        canvas.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent event) {
                if (dragStart != null && !fit) {
                    panX = panStartX + event.getX() - dragStart.x;
                    panY = panStartY + event.getY() - dragStart.y;
                    canvas.repaint();
                }
            }
            @Override
            public void mouseMoved(MouseEvent event) {
                pointerMoved(event);
            }
        });
        canvas.addMouseWheelListener(this::moveWithWheel);
        canvas.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                if (fit) {
                    panX = 0;
                    panY = 0;
                }
                canvas.repaint();
            }
        });
        videoCanvas.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                pointerMoved(event);
            }
            @Override
            public void mouseDragged(MouseEvent event) {
                pointerMoved(event);
            }
        });
    }
    private static BufferedImage loadImage(Photo photo) throws IOException {
        return ImageTransforms.orient(Imagify.read(Archives.read(photo)).toBufferedImage(), photo.orientation());
    }
    /**
     * Returns the stored placeholder for a photo, decoding it once and remembering the result.
     *
     * @param photo an unreadable photo
     * @return the placeholder image, or null when the catalog has none
     */
    private BufferedImage placeholder(Photo photo) {
        long id = photo.id();
        if (missingPlaceholders.contains(id)) {
            return null;
        }
        BufferedImage cached = placeholders.get(id);
        if (cached != null) {
            return cached;
        }
        BufferedImage loaded;
        try {
            loaded = placeholderProvider.apply(photo);
        } catch (RuntimeException e) {
            loaded = null;
        }
        if (loaded == null) {
            missingPlaceholders.add(id);
            return null;
        }
        placeholders.put(id, loaded);
        return loaded;
    }
    public void setListener(Listener listener) {
        this.listener = listener == null ? (photo, favorite) -> {
        } : listener;
    }
    /**
     * Starts the slideshow if it is not already running.
     */
    public void startSlideshow() {
        if (!slideshowTimer.isRunning()) {
            slideshowTimer.restart();
            updateSlideshowButtons(true);
        }
    }
    /**
     * Opens a viewer at a photo.
     *
     * @param owner the owner window
     * @param photos the navigation set
     * @param start the initial photo
     * @return the visible viewer
     */
    public static PhotoViewer open(Window owner, List<Photo> photos, Photo start, Listener listener) {
        return open(owner, photos, start, listener, photo -> null);
    }
    /**
     * Opens a viewer at a photo, with a fallback image for unreadable originals.
     *
     * @param owner the owner window
     * @param photos the navigation set
     * @param start the initial photo
     * @param listener receives favorite changes and the last viewed photo
     * @param placeholderProvider supplies the stored placeholder image, or null when none exists
     * @return the visible viewer
     */
    public static PhotoViewer open(Window owner, List<Photo> photos, Photo start, Listener listener,
            Function<Photo, BufferedImage> placeholderProvider) {
        PhotoViewer viewer = new PhotoViewer(owner, photos, start);
        viewer.placeholderProvider = placeholderProvider == null ? photo -> null : placeholderProvider;
        viewer.setListener(listener);
        viewer.startReveal();
        return viewer;
    }
    /**
     * Keeps the window hidden until the first decode is in place, so the viewer opens with the
     * finished photograph instead of an empty or loading frame. The timer is the safety net for
     * an unreadable file: the window still arrives, with the error on the canvas.
     */
    private void startReveal() {
        if (image != null) {
            reveal();
        } else {
            revealTimer.start();
        }
    }
    private void reveal() {
        if (revealed || closed) {
            return;
        }
        revealed = true;
        revealTimer.stop();
        coverScreen();
        if (photo != null && photo.isVideo() && player == null && !videoStarting) {
            startVideo(photo, generation.get());
        }
    }
    /**
     * Covers the screen the owner lives on without handing the display to the window manager.
     */
    private void coverScreen() {
        GraphicsDevice device = null;
        Window owner = getOwner();
        if (owner != null && owner.getGraphicsConfiguration() != null) {
            device = owner.getGraphicsConfiguration().getDevice();
        }
        if (device == null) {
            device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        }
        GraphicsConfiguration configuration = device.getDefaultConfiguration();
        setBounds(configuration.getBounds());
        setAlwaysOnTop(true);
        setVisible(true);
        positionControls();
        toFront();
        if (!requestFocusInWindow()) {
            SwingUtilities.invokeLater(this::requestFocusInWindow);
        }
    }
    private static JButton button(Icon icon, String tooltip) {
        JButton button = new JButton(icon);
        button.setToolTipText(tooltip);
        button.setForeground(new Color(0xD7DADF));
        button.setBackground(Theme.VIEWER_PANEL);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        button.setFont(Theme.font(Font.PLAIN, 11));
        return button;
    }
    private static JButton circular(Icon icon, String tooltip) {
        JButton button = new JButton(icon) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(new Color(0, 0, 0, 150));
                    g.fillOval(0, 0, getWidth(), getHeight());
                } finally {
                    g.dispose();
                }
                super.paintComponent(graphics);
            }
        };
        button.setToolTipText(tooltip);
        button.setForeground(new Color(0xD7DADF));
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setOpaque(false);
        button.setPreferredSize(new Dimension(42, 42));
        return button;
    }
    private static Icon textIcon(String text, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g.setFont(Theme.font(Font.BOLD, 10));
                    g.setColor(color);
                    java.awt.FontMetrics metrics = g.getFontMetrics();
                    int width = metrics.stringWidth(text);
                    g.drawString(text, x + (getIconWidth() - width) / 2, y + (getIconHeight() + metrics.getAscent()) / 2 - 2);
                } finally {
                    g.dispose();
                }
            }
            @Override
            public int getIconWidth() {
                return Math.max(26, 10 + text.length() * 7);
            }
            @Override
            public int getIconHeight() {
                return 16;
            }
        };
    }
    private static Icon filledSquare(int size) {
        return new Icon() {
            @Override
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setColor(Color.WHITE);
                    g.fillRect(x, y, size, size);
                } finally {
                    g.dispose();
                }
            }
            @Override
            public int getIconWidth() {
                return size;
            }
            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }
    private static Icon speaker(boolean muted) {
        return new Icon() {
            @Override
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(Color.WHITE);
                    java.awt.Polygon cone = new java.awt.Polygon();
                    cone.addPoint(x, y + 5);
                    cone.addPoint(x + 4, y + 5);
                    cone.addPoint(x + 9, y + 1);
                    cone.addPoint(x + 9, y + 13);
                    cone.addPoint(x + 4, y + 9);
                    cone.addPoint(x, y + 9);
                    g.fill(cone);
                    if (muted) {
                        g.setStroke(new java.awt.BasicStroke(1.5f));
                        g.drawLine(x + 11, y + 4, x + 16, y + 10);
                        g.drawLine(x + 16, y + 4, x + 11, y + 10);
                    } else {
                        g.drawArc(x + 9, y + 2, 6, 10, -60, 120);
                        g.drawArc(x + 9, y - 1, 10, 16, -55, 110);
                    }
                } finally {
                    g.dispose();
                }
            }
            @Override
            public int getIconWidth() {
                return 17;
            }
            @Override
            public int getIconHeight() {
                return 14;
            }
        };
    }
    private static JButton control(Icon icon, String tooltip) {
        JButton button = new JButton(icon) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(new Color(255, 255, 255, 30));
                    g.fillOval(1, 1, getWidth() - 2, getHeight() - 2);
                } finally {
                    g.dispose();
                }
                super.paintComponent(graphics);
            }
        };
        button.setToolTipText(tooltip);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setPreferredSize(new Dimension(38, 38));
        return button;
    }
    private static JButton control(String text, String tooltip) {
        JButton button = control((Icon) null, tooltip);
        button.setText(text);
        button.setFont(Theme.font(Font.PLAIN, 11));
        button.setPreferredSize(new Dimension(48, 38));
        return button;
    }
    private void installKeys() {
        bind("LEFT", () -> move(-1));
        bind("RIGHT", () -> move(1));
        bind("ESCAPE", () -> {
            if (helpVisible) {
                toggleHelp();
            } else {
                dispose();
            }
        });
        bind("SPACE", this::togglePlayback);
        bind("K", this::togglePlayback);
        bind("P", this::togglePlayback);
        bind("PLUS", () -> zoomBy(1.18));
        bind("ADD", () -> zoomBy(1.18));
        bind("MINUS", () -> zoomBy(1 / 1.18));
        bind("SUBTRACT", () -> zoomBy(1 / 1.18));
        bind("J", () -> jump(-10));
        bind("L", () -> jump(10));
        bind("[", () -> jumpChapter(-1));
        bind("]", () -> jumpChapter(1));
        bind("M", this::toggleMute);
        bind("C", this::toggleSubtitle);
        bind("UP", () -> changeVolume(5));
        bind("DOWN", () -> changeVolume(-5));
        for (int digit = 0; digit <= 9; digit++) {
            int percent = digit * 10;
            bind(Integer.toString(digit), () -> seekToPercent(percent));
        }
        bind("F", this::setFit);
        bind("shift COMMA", () -> changeSpeed(-0.25));
        bind("shift PERIOD", () -> changeSpeed(0.25));
        bind("shift SLASH", this::toggleHelp);
        bind("QUESTION", this::toggleHelp);
    }
    private void bind(String key, Runnable action) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), key);
        getRootPane().getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }
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
        stopPlayer();
        boolean ready = image != null && loadedPhotoId == photo.id();
        if (ready) {
            error = null;
        }
        fit = true;
        zoom = 1;
        panX = 0;
        panY = 0;
        updateOverlay();
        showOverlay();
        canvas.repaint();
        long token = generation.incrementAndGet();
        if (photo.isVideo()) {
            error = null;
            image = null;
            loadedPhotoId = -1;
            reveal();
            startVideo(photo, token);
            return;
        }
        if (ready) {
            return;
        }
        Photo target = photo;
        long id = target.id();
        loader.execute(() -> {
            BufferedImage loaded = null;
            String failure = null;
            if (!unreadable.contains(id)) {
                try {
                    loaded = loadImage(target);
                } catch (IOException | RuntimeException | Error e) {
                    unreadable.add(id);
                    failure = e.getMessage() == null ? "画像を読み込めません" : e.getMessage();
                }
            }
            if (loaded == null && unreadable.contains(id)) {
                BufferedImage fallback = placeholder(target);
                if (fallback != null) {
                    loaded = fallback;
                    failure = null;
                } else if (failure == null) {
                    failure = "画像を読み込めません";
                }
            }
            BufferedImage result = loaded;
            String message = failure;
            SwingUtilities.invokeLater(() -> {
                if (token != generation.get()) {
                    return;
                }
                error = message;
                image = result;
                loadedPhotoId = image == null ? -1 : id;
                if (image != null) {
                    zoom = fitScale();
                }
                canvas.repaint();
                reveal();
            });
        });
    }
    private void updateOverlay() {
        if (photo == null) {
            metadata.setText("");
            return;
        }
        metadata.setText(photo.fileName() + "     ·     " + photo.sizeLabel() + "     ·     " + photo.capturedLabel());
        boolean video = photo.isVideo();
        videoTitle.setText(withoutExtension(photo.fileName()));
        imageBottom.setVisible(!video);
        videoControls.setVisible(video && player != null);
        positionControls();
    }
    private void updateSlideshowButtons(boolean running) {
        slideshowPlay.setIcon(running ? Icons.pause(15, Theme.GOLD) : Icons.play(15, Color.WHITE));
        slideshowPlay.setToolTipText(running ? "スライドショー一時停止" : "スライドショー再生");
    }
    /** A single click toggles video playback; a double click closes the viewer. */
    private void handleScreenClick(MouseEvent event) {
        if (event.getClickCount() >= 2) {
            clickTimer.stop();
            dispose();
        } else if (photo != null && photo.isVideo() && !clickInControls(event)) {
            clickTimer.restart();
        }
    }
    /**
     * The translucent control window lets clicks land on the video canvas underneath. The bottom
     * strip belongs to the controls, so a click there must not toggle playback.
     */
    private boolean clickInControls(MouseEvent event) {
        if (!videoControls.isVisible() || !controlsWindow.isVisible()) {
            return false;
        }
        Point origin = videoControls.getLocationOnScreen();
        Rectangle area = videoControls.getBounds();
        area.setLocation(origin);
        area.grow(0, 8);
        return area.contains(event.getLocationOnScreen());
    }
    /** An mpv instance may emit identical events endlessly; only real movement reveals the controls. */
    private void pointerMoved(MouseEvent event) {
        Point now = event.getLocationOnScreen();
        if (lastPointer != null && Math.abs(now.x - lastPointer.x) <= 2 && Math.abs(now.y - lastPointer.y) <= 2) {
            return;
        }
        lastPointer = now;
        showOverlay();
    }
    private void showOverlay() {
        if (!chromeVisible) {
            setOverlayVisible(true);
        } else {
            fade(true);
        }
    }
    private void setOverlayVisible(boolean visible) {
        chromeVisible = visible;
        fade(visible);
    }
    private void fade(boolean in) {
        if (!TRANSLUCENT) {
            controlsWindow.setVisible(in);
            return;
        }
        if (in && !controlsWindow.isVisible()) {
            controlsWindow.setVisible(true);
            overlayAlpha = 0;
            controlsWindow.setOpacity(0);
        }
        fadingIn = in;
        fadeTimer.restart();
    }
    private void advanceFade() {
        overlayAlpha += fadingIn ? 0.12f : -0.12f;
        overlayAlpha = Math.max(0, Math.min(1, overlayAlpha));
        controlsWindow.setOpacity(overlayAlpha);
        if ((fadingIn && overlayAlpha >= 1) || (!fadingIn && overlayAlpha <= 0)) {
            fadeTimer.stop();
            if (!fadingIn) {
                controlsWindow.setVisible(false);
            }
        }
    }
    private void hideOverlayIfIdle() {
        boolean paused = false;
        try {
            paused = player != null && "yes".equals(player.property("pause"));
        } catch (RuntimeException ignored) {
            // The player may be mid-seek; hiding is fine in that case.
        }
        if (helpVisible || hoveringControls || seek.dragging() || volume.dragging() || paused) {
            fade(true);
            return;
        }
        setOverlayVisible(false);
    }
    /** Anchors the overlay to the full viewer bounds; its children never change the media size. */
    private void positionControls() {
        if (!isDisplayable()) {
            return;
        }
        Point origin = getLocationOnScreen();
        controlsWindow.setBounds(origin.x, origin.y, getWidth(), getHeight());
        if (helpVisible) {
            helpPanel.setBounds(0, 0, getWidth(), getHeight());
        } else {
            Dimension helpSize = helpPanel.getPreferredSize();
            helpPanel.setBounds((getWidth() - helpSize.width) / 2, (getHeight() - helpSize.height) / 2,
                    helpSize.width, helpSize.height);
        }
        Dimension toastSize = toastHost.getPreferredSize();
        toastHost.setBounds((getWidth() - toastSize.width) / 2, (getHeight() - toastSize.height) / 2,
                toastSize.width, toastSize.height);
        controlsRoot.revalidate();
    }
    private void relay(MouseEvent event) {
        Point screen = event.getLocationOnScreen();
        Point origin = canvas.getLocationOnScreen();
        canvas.dispatchEvent(new MouseEvent(canvas, event.getID(), event.getWhen(), event.getModifiersEx(),
                screen.x - origin.x, screen.y - origin.y, event.getClickCount(), event.isPopupTrigger(), event.getButton()));
    }
    private void relayWheel(MouseWheelEvent event) {
        Point screen = event.getLocationOnScreen();
        Point origin = canvas.getLocationOnScreen();
        canvas.dispatchEvent(new MouseWheelEvent(canvas, event.getID(), event.getWhen(), event.getModifiersEx(),
                screen.x - origin.x, screen.y - origin.y, 0, false, event.getScrollType(), event.getScrollAmount(),
                event.getWheelRotation()));
    }
    private static String helpHtml() {
        StringBuilder html = new StringBuilder("""
                <html><head><style>
                body { background-color:#202020; color:#dddddd; font-family:sans-serif; }
                h1 { color:white; font-size:22px; margin:0 0 18px 0; }
                h2 { color:#aaaaaa; font-size:13px; font-weight:normal; margin:14px 0 6px 0; }
                table { width:100%; border-collapse:collapse; }
                td { padding:5px 0; font-size:13px; border-bottom:1px solid #3a3a3a; }
                td.k { text-align:right; color:white; font-weight:bold; width:40%; }
                table.outer td { vertical-align:top; border:0; padding:0 20px; }
                </style></head><body>
                <h1>キーボード ショートカット</h1>
                <table class='outer'><tr><td>
                """);
        html.append("<h2>再生</h2><table>");
        helpRow(html, "再生／一時停止", "Space / K / P");
        helpRow(html, "10秒戻る", "J");
        helpRow(html, "10秒進む", "L");
        helpRow(html, "前のチャプター", "[");
        helpRow(html, "次のチャプター", "]");
        helpRow(html, "前後の項目", "← / →");
        helpRow(html, "指定位置へ移動", "0 〜 9");
        helpRow(html, "フィット表示", "F");
        html.append("</table></td><td><h2>操作</h2><table>");
        helpRow(html, "音量を上げる／下げる", "↑ / ↓");
        helpRow(html, "ミュート切替", "M");
        helpRow(html, "字幕 On／Off", "C");
        helpRow(html, "再生速度 ±0.25", "Shift + &lt; / &gt;");
        helpRow(html, "この一覧", "?");
        helpRow(html, "閉じる", "Esc");
        html.append("</table></td></tr></table></body></html>");
        return html.toString();
    }
    private static void helpRow(StringBuilder html, String label, String key) {
        html.append("<tr><td>").append(label).append("</td><td class='k'>").append(key).append("</td></tr>");
    }
    private void toggleHelp() {
        helpVisible = !helpVisible;
        helpPanel.setVisible(helpVisible);
        topPanel.setVisible(!helpVisible);
        bottomPanel.setVisible(!helpVisible);
        if (helpVisible) {
            toast.setVisible(false);
        }
        showOverlay();
        positionControls();
        controlsRoot.revalidate();
    }
    private void showToast(String text) {
        if (!isDisplayable()) {
            return;
        }
        toast.setText(text);
        toast.setVisible(true);
        showOverlay();
        positionControls();
        toastTimer.restart();
    }
    private void changeVolume(int delta) {
        if (player == null) {
            return;
        }
        int level = (int) Math.max(0, Math.min(100, Math.rint(volume.value()) + delta));
        volume.value(level);
        player.set("volume", Double.toString(level));
        updateMuteButton(false);
        showToast("音量 " + level + "%");
    }
    private void seekToPercent(int percent) {
        if (player == null) {
            return;
        }
        double duration = parseSeconds(player.property("duration"));
        if (duration <= 0) {
            return;
        }
        player.command("seek", Double.toString(duration * percent / 100), "absolute+exact");
        showToast(percent + "%");
    }
    private void togglePlayback() {
        if (player != null) {
            togglePause();
        } else if (slideshowTimer.isRunning()) {
            slideshowTimer.stop();
            updateSlideshowButtons(false);
        } else {
            startSlideshow();
        }
    }
    private void togglePause() {
        if (player == null) {
            return;
        }
        if (player.property("time-pos") == null && photo != null && !photo.archived()) {
            player.command("loadfile", photo.path().toAbsolutePath().normalize().toString(), "replace");
            player.set("pause", "no");
            updatePlayButton(false);
            showToast("再生");
            return;
        }
        boolean paused = "yes".equals(player.property("pause"));
        player.set("pause", paused ? "no" : "yes");
        updatePlayButton(!paused);
        showToast(paused ? "再生" : "一時停止");
    }
    private void updatePlayButton(boolean paused) {
        play.setIcon(paused ? Icons.play(15, Color.WHITE) : Icons.pause(15, Color.WHITE));
        play.setToolTipText(paused ? "再生" : "一時停止");
    }
    private void stopPlayback() {
        if (player == null) {
            return;
        }
        player.command("stop");
        updatePlayButton(true);
        time.setText("00:00 / 00:00");
        seek.value(0);
    }
    private void toggleMute() {
        if (player == null) {
            return;
        }
        boolean muted = "yes".equals(player.property("mute"));
        player.set("mute", muted ? "no" : "yes");
        updateMuteButton(!muted);
        showToast(muted ? "ミュート解除" : "ミュート");
    }
    private void toggleSubtitle() {
        if (player == null) {
            return;
        }
        boolean visible = !"no".equals(player.property("sub-visibility"));
        player.set("sub-visibility", visible ? "no" : "yes");
        updateSubtitleButton(!visible);
        showToast(visible ? "字幕 OFF" : "字幕 ON");
    }
    private void updateSubtitleButton(boolean visible) {
        subtitle.setIcon(textIcon("CC", visible ? Color.WHITE : new Color(0x9AA0A8)));
        subtitle.setToolTipText(visible ? "字幕を隠す" : "字幕を表示");
    }
    private void setSpeed(double value) {
        if (player == null) {
            return;
        }
        player.set("speed", Double.toString(value));
        updateSpeedButton(value);
        showToast(speedLabel(value) + "x");
    }
    private void changeSpeed(double delta) {
        if (player == null) {
            return;
        }
        double current = parseSeconds(player.property("speed"));
        if (current <= 0) {
            current = 1;
        }
        double next = Math.max(0.25, Math.min(2.0, Math.rint((current + delta) * 100) / 100));
        setSpeed(next);
    }
    private void updateSpeedButton(double value) {
        String label = speedLabel(value);
        speed.setIcon(textIcon(label + "x", value == 1 ? Color.WHITE : new Color(0x3EA6FF)));
        speed.setToolTipText("再生速度 " + label + "x");
    }
    private static String speedLabel(double value) {
        return value == Math.rint(value) ? String.format(java.util.Locale.ROOT, "%.1f", value)
                : String.format(java.util.Locale.ROOT, "%.2f", value);
    }
    private void updateMuteButton(boolean muted) {
        mute.setIcon(speaker(muted));
        mute.setToolTipText(muted ? "ミュート解除" : "ミュート");
    }
    private void setVideoControlsVisible(boolean visible) {
        videoControls.setVisible(visible && photo != null && photo.isVideo());
    }
    /**
     * Creates a libmpv instance for the current video on the background loader. The native window is
     * only available after the viewer has been realized, which is why this never runs in the
     * constructor.
     */
    private void startVideo(Photo target, long token) {
        if (videoStarting || player != null || closed) {
            return;
        }
        videoStarting = true;
        loader.execute(() -> {
            Mpv created = null;
            String failure = null;
            try {
                if (!MpvNative.isAvailable()) {
                    throw new IllegalStateException("libmpv is not available");
                }
                // The stock OSC would draw a second control bar over ours.
                created = Mpv.open(Map.of("vo", "gpu", "config", "no", "pause", "yes", "osc", "no"));
                created.set("sub-visibility", "no");
                long parent = Win32Window.window(windowTitle);
                long handle = Win32Window.child(parent, "SunAwtCanvas");
                if (handle == 0) {
                    throw new IllegalStateException("The video surface has no native window");
                }
                created.setLong("wid", handle);
                created.load(target.path());
                // Resume before playback starts so the first visible frame is the saved position.
                long saved = listener.playbackPosition(target.id());
                double length = parseSeconds(created.property("duration"));
                if (saved > 0 && length > 0) {
                    double resume = Math.min(saved / 1000.0, Math.max(0, length - 0.5));
                    if (resume > 0) {
                        awaitResumedFrame(created, resume);
                    }
                }
                created.set("pause", "no");
            } catch (Throwable e) {
                if (created != null) {
                    created.close();
                }
                created = null;
                failure = e.getMessage() == null ? "動画を再生できません" : e.getMessage();
            }
            Mpv result = created;
            String message = failure;
            SwingUtilities.invokeLater(() -> {
                videoStarting = false;
                if (token != generation.get() || closed) {
                    if (result != null) {
                        result.close();
                    }
                    return;
                }
                if (result == null) {
                    error = message;
                    canvas.repaint();
                    return;
                }
                player = result;
                playingPhotoId = target.id();
                videoCanvas.setVisible(true);
                showOverlay();
                updatePlayButton(false);
                volume.value(parseInt(player.property("volume"), 100));
                updateMuteButton("yes".equals(player.property("mute")));
                updateSubtitleButton(false);
                updateSpeedButton(parseSeconds(player.property("speed")));
                videoTitle.setText(photo == null ? "" : photo.fileName());
                chapters = player.chapters();
                double length = parseSeconds(player.property("duration"));
                seek.marks(chapters.stream().map(chapter -> length > 0
                        ? Math.max(0, Math.min(1, chapter.time() / length)) : 0).toList());
                progress.start();
                updateProgress();
                updateOverlay();
                reveal();
            });
        });
    }
    /**
     * Waits until the frame at the resume position has really been decoded. mpv updates
     * {@code time-pos} the moment a seek is issued, while the picture still shows the first frame;
     * handing the window over at that point made the opening frame flash briefly.
     */
    private static void awaitResumedFrame(Mpv mpv, double resume) throws IOException {
        Path directory = Files.createTempDirectory("freja-resume-");
        Path before = directory.resolve("before.png");
        Path after = directory.resolve("after.png");
        try {
            byte[] baseline = mpv.capture(before);
            mpv.command("seek", Double.toString(resume), "absolute+exact");
            long deadline = System.nanoTime() + 3_000_000_000L;
            while (System.nanoTime() < deadline && Arrays.equals(baseline, mpv.capture(after))) {
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                Files.deleteIfExists(before);
                Files.deleteIfExists(after);
                Files.deleteIfExists(directory);
            } catch (IOException ignored) {
            }
        }
    }
    private void stopPlayer() {
        progress.stop();
        videoStarting = false;
        videoCanvas.setVisible(false);
        chapters = List.of();
        seek.marks(List.of());
        setVideoControlsVisible(false);
        Mpv old = player;
        player = null;
        if (old != null) {
            double position = parseSeconds(old.property("time-pos"));
            double length = parseSeconds(old.property("duration"));
            // A video watched to the end starts from the beginning the next time.
            long millis = length > 0 && position >= length - 0.5 ? 0 : Math.round(position * 1000);
            long id = playingPhotoId;
            playingPhotoId = -1;
            old.close();
            if (id >= 0) {
                saves.execute(() -> listener.onPlaybackPosition(id, millis));
            }
        }
        updateOverlay();
    }
    private void updateProgress() {
        if (player == null) {
            return;
        }
        try {
            double position = parseSeconds(player.property("time-pos"));
            double duration = parseSeconds(player.property("duration"));
            if (seek.dragging()) {
                // The drag preview owns the time label and the knob while the user scrubs.
                return;
            }
            String text = clock(position) + " / " + clock(duration);
            if (!text.equals(time.getText())) {
                time.setText(text);
            }
            updatePlayButton("yes".equals(player.property("pause")));
            seek.value(duration > 0 ? position / duration * 1000 : 0);
        } catch (RuntimeException ignored) {
            // A property can be briefly unavailable while a new file is being loaded.
        }
    }
    private static double parseSeconds(String value) {
        try {
            return value == null ? 0 : Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
    private static String withoutExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
    private static String clock(double seconds) {
        long whole = (long) seconds;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", whole / 60, whole % 60);
    }
    private void setFit() {
        fit = true;
        zoom = image == null ? 1 : fitScale();
        panX = 0;
        panY = 0;
        canvas.repaint();
    }
    private void zoomBy(double factor) {
        if (image == null) {
            return;
        }
        fit = false;
        zoom = Math.max(0.05, Math.min(8, (fit ? fitScale() : zoom) * factor));
        canvas.repaint();
    }
    /** Wheel up goes to the previous photo, wheel down to the next one. */
    private void moveWithWheel(MouseWheelEvent event) {
        int rotation = event.getWheelRotation();
        if (rotation == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastWheelMove < 120) {
            return;
        }
        lastWheelMove = now;
        move(rotation < 0 ? -1 : 1);
    }
    private double fitScale() {
        if (image == null) {
            return 1;
        }
        double width = Math.max(100, canvas.getWidth() - 80);
        double height = Math.max(100, canvas.getHeight() - 60);
        return Math.min(2.5, Math.min(width / image.getWidth(), height / image.getHeight()));
    }
    @Override
    public void dispose() {
        if (closed) {
            return;
        }
        closed = true;
        stopPlayer();
        // Graceful shutdown lets the last position write finish before the viewer goes away.
        saves.shutdown();
        chromeTimer.stop();
        toastTimer.stop();
        clickTimer.stop();
        fadeTimer.stop();
        revealTimer.stop();
        slideshowTimer.stop();
        progress.stop();
        generation.incrementAndGet();
        loader.shutdownNow();
        Window owner = getOwner();
        setVisible(false);
        setAlwaysOnTop(false);
        controlsWindow.dispose();
        super.dispose();
        listener.onClose(photo);
        if (owner instanceof Frame frame) {
            frame.toFront();
            frame.requestFocusInWindow();
        } else if (owner != null) {
            owner.toFront();
        }
    }
    private void applyVolume() {
        if (player != null) {
            player.set("volume", Double.toString(volume.value()));
        }
    }
    private void previewSeek() {
        if (player == null) {
            return;
        }
        double duration = parseSeconds(player.property("duration"));
        double target = duration * seek.value() / 1000;
        time.setText(clock(target) + " / " + clock(duration));
        long now = System.nanoTime();
        if (now - lastScrubNanos >= 60_000_000L) {
            lastScrubNanos = now;
            // Exact seeks keep the picture in step with the knob; throttled so they stay smooth.
            player.command("seek", Double.toString(target), "absolute+exact");
        }
    }
    private void commitSeek() {
        if (player == null) {
            return;
        }
        lastScrubNanos = 0;
        double duration = parseSeconds(player.property("duration"));
        player.command("seek", Double.toString(duration * seek.value() / 1000), "absolute+exact");
    }
    private void jump(double seconds) {
        if (player == null) {
            return;
        }
        double duration = parseSeconds(player.property("duration"));
        double position = parseSeconds(player.property("time-pos"));
        if (duration <= 0) {
            return;
        }
        double target = Math.max(0, Math.min(duration - 0.05, position + seconds));
        player.command("seek", Double.toString(target), "absolute+exact");
        showToast(seconds < 0 ? "10秒戻る" : "10秒進む");
    }
    /** Moves to the previous or next chapter, showing its title when mpv reports one. */
    private void jumpChapter(int delta) {
        if (player == null) {
            return;
        }
        if (chapters.isEmpty()) {
            showToast("チャプターがありません");
            return;
        }
        double position = parseSeconds(player.property("time-pos"));
        int current = -1;
        for (int i = 0; i < chapters.size(); i++) {
            if (chapters.get(i).time() <= position + 0.2) {
                current = i;
            }
        }
        int target = Math.max(0, Math.min(chapters.size() - 1, current + delta));
        player.chapter(target);
        Mpv.Chapter chapter = chapters.get(target);
        showToast(chapter.title().isEmpty() ? "チャプター " + (target + 1) : chapter.title());
    }
    /**
     * A double-valued slider so the knob follows the pointer exactly. Swing's JSlider works on
     * integer values, which made the handle jump in visible steps, and its partial repaints left
     * the old knob behind.
     */
    private final class Slider extends JComponent {
        /** Half the knob width; the track is inset by this much so the knob stays inside the bar. */
        private static final int KNOB = 6;
        private double minimum;
        private double maximum = 1;
        private double value;
        private boolean dragging;
        /** Chapter start positions as a fraction of the track; empty for other sliders. */
        private List<Double> marks = List.of();
        private final Runnable onChange;
        private final Runnable onRelease;
        Slider(Runnable onChange, Runnable onRelease) {
            this.onChange = onChange;
            this.onRelease = onRelease;
            setOpaque(false);
            MouseAdapter pointer = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    dragging = true;
                    update(event.getX());
                }
                @Override
                public void mouseDragged(MouseEvent event) {
                    update(event.getX());
                }
                @Override
                public void mouseReleased(MouseEvent event) {
                    dragging = false;
                    onRelease.run();
                }
            };
            addMouseListener(pointer);
            addMouseMotionListener(pointer);
        }
        void setRange(double min, double max) {
            minimum = min;
            maximum = max;
        }
        void marks(List<Double> positions) {
            marks = positions;
            repaint();
        }
        double value() {
            return value;
        }
        void value(double next) {
            double clamped = Math.max(minimum, Math.min(maximum, next));
            if (clamped == value) {
                return;
            }
            value = clamped;
            repaint();
        }
        boolean dragging() {
            return dragging;
        }
        private void update(int x) {
            double track = Math.max(1, getWidth() - 2 * KNOB);
            double fraction = clampFraction((double) (x - KNOB) / track);
            double next = minimum + (maximum - minimum) * fraction;
            if (next != value) {
                value = next;
                repaint();
            }
            onChange.run();
        }
        private double clampFraction(double fraction) {
            return Math.max(0, Math.min(1, fraction));
        }
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                // Keep the strip transparent: the video shows through, and clicks in this area are
                // picked up by the viewer's seek catcher instead of a painted, opaque overlay.
                g.setComposite(AlphaComposite.Clear);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setComposite(AlphaComposite.SrcOver);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int width = getWidth();
                int height = getHeight();
                // Keep the painted bar at its old position while the hit target grows upward.
                int trackY = height - 9;
                int trackWidth = Math.max(1, width - 2 * KNOB);
                double fraction = maximum > minimum ? (value - minimum) / (maximum - minimum) : 0;
                double centre = KNOB + fraction * trackWidth;
                g.setColor(new Color(255, 255, 255, 90));
                g.fillRoundRect(KNOB, trackY - 2, trackWidth, 4, 3, 3);
                g.setColor(new Color(0x3EA6FF));
                g.fillRoundRect(KNOB, trackY - 2, (int) Math.round(centre - KNOB), 4, 3, 3);
                for (double mark : marks) {
                    int x = (int) Math.round(KNOB + mark * trackWidth);
                    g.setColor(new Color(0, 0, 0, 110));
                    g.fillRect(x - 1, trackY - 5, 3, 10);
                    g.setColor(new Color(255, 255, 255, 190));
                    g.fillRect(x, trackY - 4, 1, 8);
                }
                g.setColor(new Color(0x3EA6FF));
                g.fillOval((int) Math.round(centre) - KNOB, trackY - 6, 2 * KNOB, 2 * KNOB);
            } finally {
                g.dispose();
            }
        }
    }
    private final class Canvas extends JComponent {
        Canvas() {
            setBackground(Theme.VIEWER);
            setOpaque(true);
        }
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setColor(Theme.VIEWER);
                g.fillRect(0, 0, getWidth(), getHeight());
                if (image == null) {
                    if (error != null) {
                        paintCentered(g, "画像を読み込めません", new Color(0xC9CDD3));
                        paintCentered(g, error, new Color(0x777D86), 24);
                    } else if (photo == null || !photo.isVideo()) {
                        paintCentered(g, "写真を選択してください", Theme.FAINT);
                    }
                    return;
                }
                double scale = fit ? fitScale() : zoom;
                int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
                int h = Math.max(1, (int) Math.round(image.getHeight() * scale));
                int x = (getWidth() - w) / 2 + (int) Math.round(panX);
                int y = (getHeight() - h) / 2 + (int) Math.round(panY);
                g.setColor(new Color(0, 0, 0, 130));
                g.fillRoundRect(x + 6, y + 10, w, h, 8, 8);
                g.drawImage(image, x, y, w, h, null);
            } finally {
                g.dispose();
            }
        }
        private void paintCentered(Graphics2D g, String text, Color color) {
            paintCentered(g, text, color, 0);
        }
        private void paintCentered(Graphics2D g, String text, Color color, int offsetY) {
            g.setFont(Theme.font(Font.PLAIN, 13));
            g.setColor(color);
            int width = g.getFontMetrics().stringWidth(text);
            g.drawString(text, (getWidth() - width) / 2, getHeight() / 2 + offsetY);
        }
    }
}