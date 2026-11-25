/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import freja.model.Photo;
import freja.util.ImageTransforms;
import imagify.Imagify;

/**
 * A photo viewer which covers the whole screen, with zoom, pan, keyboard navigation and a
 * slideshow. It does so as a top-most window over the owner's screen rather than through the
 * display's exclusive full-screen mode, so leaving it does not disturb the desktop.
 */
public final class PhotoViewer extends JDialog {

    /** Receives changes made while the viewer is open. */
    public interface Listener {

        void onFavorite(Photo photo, boolean favorite);
    }

    private final List<Photo> photos;

    private int index;

    private final Canvas canvas = new Canvas();

    private final JLabel title = new JLabel();

    private final JLabel counter = new JLabel();

    private final JButton favorite = button(Icons.star(17, Theme.FAINT, false), "お気に入り");

    private final JButton slideshow = button(Icons.play(15, Color.WHITE), "スライドショー");

    private final ExecutorService loader = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "freja-viewer");
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
        setIconImage(Icons.applicationImage());
        this.photos = List.copyOf(photos);
        this.index = Math.max(0, this.photos.indexOf(start));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setUndecorated(true);
        setBackground(Theme.VIEWER);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Theme.VIEWER);

        JPanel toolbar = new JPanel(new BorderLayout(12, 0));
        toolbar.setBackground(Theme.VIEWER_PANEL);
        toolbar.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 12));

        JPanel labels = new JPanel();
        labels.setOpaque(false);
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
        title.setForeground(Color.WHITE);
        title.setFont(Theme.font(Font.BOLD, 14));
        counter.setForeground(new Color(0x9AA0A8));
        counter.setFont(Theme.font(Font.PLAIN, 11));
        labels.add(title);
        labels.add(Box.createVerticalStrut(2));
        labels.add(counter);
        toolbar.add(labels, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.setOpaque(false);
        JButton previous = button(Icons.chevron(18, Color.WHITE, true), "前へ");
        previous.addActionListener(event -> move(-1));
        JButton next = button(Icons.chevron(18, Color.WHITE, false), "次へ");
        next.addActionListener(event -> move(1));
        favorite.addActionListener(event -> toggleFavorite());
        slideshow.addActionListener(event -> toggleSlideshow());
        JButton fitButton = button(null, "画像全体を表示");
        fitButton.setText("フィット");
        fitButton.setFont(Theme.font(Font.PLAIN, 11));
        fitButton.addActionListener(event -> setFit());
        JButton close = button(Icons.close(16, Color.WHITE), "閉じる");
        close.addActionListener(event -> dispose());
        actions.add(previous);
        actions.add(next);
        actions.add(favorite);
        actions.add(fitButton);
        actions.add(slideshow);
        actions.add(close);
        toolbar.add(actions, BorderLayout.EAST);
        add(toolbar, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 7));
        footer.setBackground(Theme.VIEWER_PANEL);
        JLabel help = new JLabel("← → またはスクロールで移動   ·   ドラッグで移動   ·   + / - でズーム   ·   Space でスライドショー   ·   Esc で閉じる");
        help.setForeground(new Color(0x858B94));
        help.setFont(Theme.font(Font.PLAIN, 11));
        footer.add(help);
        add(footer, BorderLayout.SOUTH);

        slideshowTimer = new Timer(3600, event -> move(1));
        slideshowTimer.setInitialDelay(3600);
        revealTimer = new Timer(REVEAL_DELAY_MS, event -> reveal());
        revealTimer.setRepeats(false);

        installKeys();
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                dragStart = event.getPoint();
                panStartX = panX;
                panStartY = panY;
                if (event.getClickCount() == 2) {
                    dispose();
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                dragStart = null;
            }
        });
        canvas.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent event) {
                if (dragStart != null && !fit) {
                    panX = panStartX + event.getX() - dragStart.x;
                    panY = panStartY + event.getY() - dragStart.y;
                    canvas.repaint();
                }
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

        // Never read a file on the event dispatch thread. Nothing is painted until the full decode
        // arrives: showing the cached thumbnail in the meantime meant a small, stretched version of
        // the photo flashed ahead of the real one.
        showPhoto(index);
    }

    private static BufferedImage loadImage(Photo photo) throws IOException {
        return ImageTransforms.orient(Imagify.read(Files.readAllBytes(photo.path())).toBufferedImage(), photo.orientation());
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
            toggleSlideshow();
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
        PhotoViewer viewer = new PhotoViewer(owner, photos, start);
        viewer.setListener(listener);
        viewer.startReveal();
        return viewer;
    }

    /**
     * Keeps the window hidden until the first full image has been decoded, so the viewer opens with
     * the finished photograph instead of an empty or loading frame. The timer is the safety net for
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
    }

    /**
     * Covers the screen the owner lives on without handing the display to the window manager. This
     * viewer never calls {@code setDisplayMode}, so exclusive full-screen mode would buy nothing
     * except the mode switch: on the way out Windows restores the desktop and repaints the window
     * underneath in pieces, which is what made the main screen flicker after the viewer closed.
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
        toFront();
        // A window which has just been shown does not always accept focus on the first attempt,
        // and the keyboard navigation below depends on it.
        if (!requestFocusInWindow()) {
            SwingUtilities.invokeLater(this::requestFocusInWindow);
        }
    }

    private static JButton button(javax.swing.Icon icon, String tooltip) {
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

    private void installKeys() {
        bind("LEFT", () -> move(-1));
        bind("RIGHT", () -> move(1));
        bind("ESCAPE", this::dispose);
        bind("SPACE", this::toggleSlideshow);
        bind("PLUS", () -> zoomBy(1.18));
        bind("ADD", () -> zoomBy(1.18));
        bind("MINUS", () -> zoomBy(1 / 1.18));
        bind("SUBTRACT", () -> zoomBy(1 / 1.18));
        bind("0", this::setFit);
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
        boolean ready = image != null && loadedPhotoId == photo.id();
        // Keep the previous frame on screen until the next one is decoded; clearing it here makes
        // every arrow press flash the viewer background.
        if (ready) {
            error = null;
        }
        fit = true;
        zoom = 1;
        panX = 0;
        panY = 0;
        updateToolbar();
        canvas.repaint();
        if (ready) {
            return;
        }

        Photo target = photo;
        long id = target.id();
        long token = generation.incrementAndGet();
        loader.execute(() -> {
            BufferedImage loaded = null;
            String failure = null;
            try {
                loaded = loadImage(target);
            } catch (IOException | RuntimeException | Error e) {
                failure = e.getMessage() == null ? "画像を読み込めません" : e.getMessage();
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
                // The first decoded frame is what the window opens on; later navigations find the
                // window already revealed and this call does nothing.
                reveal();
            });
        });
    }

    private void updateToolbar() {
        if (photo == null) {
            title.setText("");
            counter.setText("");
            return;
        }
        title.setText(photo.fileName());
        counter.setText((index + 1) + " / " + photos.size() + "   ·   " + photo.dimensionsLabel() + "   ·   " + photo.sizeLabel());
        favorite.setIcon(Icons.star(17, photo.favorite() ? Theme.GOLD : Theme.FAINT, photo.favorite()));
        favorite.setToolTipText(photo.favorite() ? "お気に入りから外す" : "お気に入りに追加");
    }

    private void toggleFavorite() {
        if (photo == null) {
            return;
        }
        photo = photo.withFavorite(!photo.favorite());
        updateToolbar();
        listener.onFavorite(photo, photo.favorite());
        canvas.repaint();
    }

    private void toggleSlideshow() {
        if (slideshowTimer.isRunning()) {
            slideshowTimer.stop();
            slideshow.setIcon(Icons.play(15, Color.WHITE));
            slideshow.setToolTipText("スライドショー");
        } else {
            slideshowTimer.restart();
            slideshow.setIcon(Icons.pause(15, Theme.GOLD));
            slideshow.setToolTipText("スライドショーを停止");
        }
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
        closed = true;
        revealTimer.stop();
        slideshowTimer.stop();
        generation.incrementAndGet();
        loader.shutdownNow();
        // Leave the screen and give up the top-most rank before the window goes away, then hand
        // activation back to the owner in one step, so the main window is shown once fully painted
        // instead of being expose-painted while the viewer is still on top of it.
        Window owner = getOwner();
        setVisible(false);
        setAlwaysOnTop(false);
        super.dispose();
        if (owner instanceof Frame frame) {
            frame.toFront();
            frame.requestFocusInWindow();
        } else if (owner != null) {
            owner.toFront();
        }
    }

    /**
     * The painting surface.
     */
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
                    // Nothing intermediate is painted: the window only appears once the full image
                    // is ready, or when there is an error to report.
                    if (error != null) {
                        paintCentered(g, "画像を読み込めません", new Color(0xC9CDD3));
                        paintCentered(g, error, new Color(0x777D86), 24);
                    } else {
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
