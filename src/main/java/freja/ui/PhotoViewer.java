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
import java.awt.Dimension;
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
 * A full screen photo viewer with zoom, pan, keyboard navigation and a slideshow.
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

    private final AtomicLong generation = new AtomicLong();

    private final Timer slideshowTimer;

    private Photo photo;

    private BufferedImage image;

    private boolean loading;

    private String error;

    private boolean fit = true;

    private double zoom = 1;

    private Point dragStart;

    private double panX;

    private double panY;

    private double panStartX;

    private double panStartY;

    private Listener listener = (photo, favorite) -> {
    };

    public PhotoViewer(Window owner, List<Photo> photos, Photo start) {
        super(owner, "Freja", ModalityType.MODELESS);
        this.photos = List.copyOf(photos);
        this.index = Math.max(0, this.photos.indexOf(start));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setBackground(Theme.VIEWER);
        Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.max(720, Math.min(1280, screen.width - 80)), Math.max(480, Math.min(860, screen.height - 80)));
        setLocationRelativeTo(owner);
        setMinimumSize(new Dimension(720, 480));
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
        JLabel help = new JLabel("← → で移動   ·   スクロールでズーム   ·   ドラッグで移動   ·   Space でスライドショー   ·   Esc で閉じる");
        help.setForeground(new Color(0x858B94));
        help.setFont(Theme.font(Font.PLAIN, 11));
        footer.add(help);
        add(footer, BorderLayout.SOUTH);

        slideshowTimer = new Timer(3600, event -> move(1));
        slideshowTimer.setInitialDelay(3600);

        installKeys();
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                dragStart = event.getPoint();
                panStartX = panX;
                panStartY = panY;
                if (event.getClickCount() == 2) {
                    toggleZoom();
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
        canvas.addMouseWheelListener(this::zoomWithWheel);
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

        showPhoto(index);
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
        viewer.setVisible(true);
        viewer.toFront();
        return viewer;
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
        image = null;
        loading = true;
        error = null;
        fit = true;
        zoom = 1;
        panX = 0;
        panY = 0;
        updateToolbar();
        canvas.repaint();

        long token = generation.incrementAndGet();
        loader.execute(() -> {
            BufferedImage loaded = null;
            String failure = null;
            try {
                BufferedImage decoded = Imagify.read(photo.path()).toBufferedImage();
                loaded = ImageTransforms.orient(decoded, photo.orientation());
            } catch (RuntimeException | Error e) {
                failure = e.getMessage() == null ? "画像を読み込めません" : e.getMessage();
            }
            BufferedImage result = loaded;
            String message = failure;
            SwingUtilities.invokeLater(() -> {
                if (token != generation.get()) {
                    return;
                }
                loading = false;
                error = message;
                image = result;
                if (image != null) {
                    zoom = fitScale();
                }
                canvas.repaint();
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

    private void toggleZoom() {
        if (fit) {
            fit = false;
            zoom = 1;
            panX = 0;
            panY = 0;
        } else {
            setFit();
        }
        canvas.repaint();
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

    private void zoomWithWheel(MouseWheelEvent event) {
        if (image == null) {
            return;
        }
        zoomBy(event.getWheelRotation() < 0 ? 1.12 : 1 / 1.12);
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
        slideshowTimer.stop();
        generation.incrementAndGet();
        loader.shutdownNow();
        super.dispose();
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
                if (loading) {
                    paintCentered(g, "読み込み中…", Theme.FAINT);
                    return;
                }
                if (error != null) {
                    paintCentered(g, "画像を読み込めません", new Color(0xC9CDD3));
                    paintCentered(g, error, new Color(0x777D86), 24);
                    return;
                }
                if (image == null) {
                    paintCentered(g, "写真を選択してください", Theme.FAINT);
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
