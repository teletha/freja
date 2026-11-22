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

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.formdev.flatlaf.FlatClientProperties;

import freja.model.Photo;

/**
 * The details panel shown next to the thumbnail grid.
 */
public final class PhotoInspector extends JPanel {

    /** User actions initiated from the inspector. */
    public interface Listener {

        void onOpen(Photo photo);

        void onReveal(Photo photo);

        void onFavorite(Photo photo, boolean favorite);

        void onRating(Photo photo, int rating);

        void onCaption(Photo photo, String caption);
    }

    private final Preview preview;

    private final JLabel fileName = new JLabel();

    private final JLabel folderName = new JLabel();

    private final JLabel dateValue = infoValue();

    private final JLabel dimensionsValue = infoValue();

    private final JLabel sizeValue = infoValue();

    private final JLabel cameraValue = infoValue();

    private final JLabel lensValue = infoValue();

    private final JLabel exposureValue = infoValue();

    private final JTextArea caption = new JTextArea();

    private final JButton saveCaption = new JButton("保存");

    private final JButton favorite = new JButton("お気に入り");

    private final JButton open = new JButton("開く");

    private final JButton[] stars = new JButton[5];

    private Photo photo;

    private Listener listener = new Listener() {
        @Override
        public void onOpen(Photo photo) {
        }

        @Override
        public void onReveal(Photo photo) {
        }

        @Override
        public void onFavorite(Photo photo, boolean favorite) {
        }

        @Override
        public void onRating(Photo photo, int rating) {
        }

        @Override
        public void onCaption(Photo photo, String caption) {
        }
    };

    private boolean updating;

    public PhotoInspector(ThumbnailCache cache) {
        setLayout(new BorderLayout());
        setBackground(Theme.SURFACE);
        setPreferredSize(new Dimension(308, 600));
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.BORDER));

        preview = new Preview(cache);
        preview.setPreferredSize(new Dimension(300, 238));
        preview.setMinimumSize(new Dimension(200, 150));
        preview.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
        preview.setAlignmentX(LEFT_ALIGNMENT);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Theme.SURFACE);
        body.setBorder(BorderFactory.createEmptyBorder(14, 16, 18, 16));
        body.add(preview);
        body.add(Box.createVerticalStrut(14));

        fileName.setFont(Theme.font(Font.BOLD, 16));
        fileName.setForeground(Theme.TEXT);
        fileName.setAlignmentX(LEFT_ALIGNMENT);
        body.add(fileName);
        body.add(Box.createVerticalStrut(4));
        folderName.setFont(Theme.font(Font.PLAIN, 11));
        folderName.setForeground(Theme.MUTED);
        folderName.setAlignmentX(LEFT_ALIGNMENT);
        body.add(folderName);
        body.add(Box.createVerticalStrut(12));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        open.putClientProperty(FlatClientProperties.STYLE, "background:#2E6BE6; foreground:#FFFFFF; borderWidth:0; arc:9; hoverBackground:#1F52B8; pressedBackground:#1A459B");
        open.setFont(Theme.font(Font.BOLD, 12));
        open.addActionListener(event -> {
            if (photo != null) {
                listener.onOpen(photo);
            }
        });
        actions.add(open);
        favorite.setFont(Theme.font(Font.PLAIN, 12));
        favorite.addActionListener(event -> toggleFavorite());
        actions.add(favorite);
        body.add(actions);
        body.add(Box.createVerticalStrut(10));

        JPanel rating = new JPanel(new FlowLayout(FlowLayout.LEFT, 1, 0));
        rating.setOpaque(false);
        rating.setAlignmentX(LEFT_ALIGNMENT);
        JLabel ratingLabel = new JLabel("評価");
        ratingLabel.setFont(Theme.font(Font.PLAIN, 11));
        ratingLabel.setForeground(Theme.MUTED);
        ratingLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 7));
        rating.add(ratingLabel);
        for (int i = 0; i < stars.length; i++) {
            int value = i + 1;
            JButton star = new JButton(Icons.star(16, Theme.FAINT, false));
            star.setContentAreaFilled(false);
            star.setBorder(BorderFactory.createEmptyBorder(2, 3, 2, 3));
            star.setFocusable(false);
            star.setToolTipText(value + " つ星");
            star.addActionListener(event -> setRating(value));
            stars[i] = star;
            rating.add(star);
        }
        body.add(rating);
        body.add(Box.createVerticalStrut(10));

        JPanel info = new JPanel(new GridBagLayout());
        info.setOpaque(false);
        info.setAlignmentX(LEFT_ALIGNMENT);
        addInfo(info, "撮影日", dateValue);
        addInfo(info, "サイズ", dimensionsValue);
        addInfo(info, "容量", sizeValue);
        addInfo(info, "カメラ", cameraValue);
        addInfo(info, "レンズ", lensValue);
        addInfo(info, "露出", exposureValue);
        body.add(info);
        body.add(Box.createVerticalStrut(12));

        JLabel captionLabel = new JLabel("キャプション");
        captionLabel.setFont(Theme.font(Font.BOLD, 11));
        captionLabel.setForeground(Theme.MUTED);
        captionLabel.setAlignmentX(LEFT_ALIGNMENT);
        body.add(captionLabel);
        body.add(Box.createVerticalStrut(5));
        caption.setLineWrap(true);
        caption.setWrapStyleWord(true);
        caption.setFont(Theme.font(Font.PLAIN, 12));
        caption.setForeground(Theme.TEXT);
        caption.setBackground(Theme.BACKGROUND);
        caption.setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 8));
        caption.setRows(3);
        caption.setAlignmentX(LEFT_ALIGNMENT);
        caption.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        caption.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                if (saveCaption.isEnabled()) {
                    commitCaption();
                }
            }
        });
        caption.getInputMap().put(KeyStroke.getKeyStroke("control ENTER"), "save");
        caption.getActionMap().put("save", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                saveCaption.doClick();
            }
        });
        body.add(caption);
        body.add(Box.createVerticalStrut(6));

        saveCaption.setFont(Theme.font(Font.PLAIN, 11));
        saveCaption.setEnabled(false);
        saveCaption.setAlignmentX(LEFT_ALIGNMENT);
        saveCaption.addActionListener(event -> commitCaption());
        body.add(saveCaption);
        body.add(Box.createVerticalStrut(12));

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        footer.setOpaque(false);
        footer.setAlignmentX(LEFT_ALIGNMENT);
        JButton reveal = new JButton("エクスプローラーで表示");
        reveal.setFont(Theme.font(Font.PLAIN, 11));
        reveal.setContentAreaFilled(false);
        reveal.setForeground(Theme.ACCENT);
        reveal.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        reveal.addActionListener(event -> {
            if (photo != null) {
                listener.onReveal(photo);
            }
        });
        footer.add(reveal);
        body.add(footer);
        body.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(28);
        scroll.setBackground(Theme.SURFACE);
        add(scroll, BorderLayout.CENTER);

        DocumentListener changed = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updateSaveState();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updateSaveState();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updateSaveState();
            }
        };
        caption.getDocument().addDocumentListener(changed);
        show(null);
    }

    public void setListener(Listener listener) {
        this.listener = listener == null ? this.listener : listener;
    }

    /**
     * Shows a photo, or clears the inspector.
     *
     * @param photo the selected photo, or null
     */
    public void show(Photo photo) {
        this.photo = photo;
        updating = true;
        try {
            if (photo == null) {
                fileName.setText("写真を選択してください");
                fileName.setForeground(Theme.MUTED);
                folderName.setText(" ");
                dateValue.setText("—");
                dimensionsValue.setText("—");
                sizeValue.setText("—");
                cameraValue.setText("—");
                lensValue.setText("—");
                exposureValue.setText("—");
                caption.setText("");
                caption.setEnabled(false);
                saveCaption.setEnabled(false);
                open.setEnabled(false);
                favorite.setEnabled(false);
                favorite.setText("お気に入り");
                favorite.setIcon(Icons.star(15, Theme.FAINT, false));
                for (JButton star : stars) {
                    star.setEnabled(false);
                    star.setIcon(Icons.star(16, Theme.FAINT, false));
                }
            } else {
                fileName.setText(html(photo.fileName(), 255));
                fileName.setForeground(Theme.TEXT);
                folderName.setText(shorten(photo.folderPath(), 40));
                folderName.setToolTipText(photo.folderPath());
                dateValue.setText(photo.capturedLabel());
                dimensionsValue.setText(photo.dimensionsLabel() + "  ·  " + photo.formatLabel());
                sizeValue.setText(photo.sizeLabel());
                cameraValue.setText(photo.cameraLabel() == null ? "—" : html(photo.cameraLabel(), 170));
                lensValue.setText(photo.lens() == null ? "—" : html(photo.lens(), 170));
                exposureValue.setText(exposure(photo));
                caption.setText(photo.caption() == null ? "" : photo.caption());
                caption.setEnabled(true);
                open.setEnabled(true);
                favorite.setEnabled(true);
                favorite.setText(photo.favorite() ? "お気に入り済み" : "お気に入り");
                favorite.setIcon(Icons.star(15, photo.favorite() ? Theme.GOLD : Theme.FAINT, photo.favorite()));
                updateStars(photo.rating());
                for (JButton star : stars) {
                    star.setEnabled(true);
                }
                saveCaption.setEnabled(false);
            }
        } finally {
            updating = false;
        }
        preview.show(photo);
        revalidate();
        repaint();
    }

    private void updateStars(int rating) {
        for (int i = 0; i < stars.length; i++) {
            stars[i].setIcon(Icons.star(16, i < rating ? Theme.GOLD : Theme.FAINT, i < rating));
        }
    }

    private void toggleFavorite() {
        if (photo == null) {
            return;
        }
        Photo updated = photo.withFavorite(!photo.favorite());
        show(updated);
        listener.onFavorite(updated, updated.favorite());
    }

    private void setRating(int rating) {
        if (photo == null) {
            return;
        }
        int value = photo.rating() == rating ? 0 : rating;
        Photo updated = photo.withRating(value);
        show(updated);
        listener.onRating(updated, value);
    }

    private void commitCaption() {
        if (photo == null) {
            return;
        }
        String value = caption.getText().trim();
        Photo updated = photo.withCaption(value.isEmpty() ? null : value);
        show(updated);
        saveCaption.setEnabled(false);
        listener.onCaption(updated, value);
    }

    private void updateSaveState() {
        if (updating || photo == null) {
            return;
        }
        String current = photo.caption() == null ? "" : photo.caption();
        saveCaption.setEnabled(!current.equals(caption.getText().trim()));
    }

    private String exposure(Photo photo) {
        StringBuilder builder = new StringBuilder();
        if (photo.exposure() != null) {
            builder.append(photo.exposure());
        }
        if (photo.aperture() != null) {
            if (builder.length() > 0) {
                builder.append("  ·  ");
            }
            builder.append("f/").append(trim(photo.aperture()));
        }
        if (photo.iso() != null) {
            if (builder.length() > 0) {
                builder.append("  ·  ");
            }
            builder.append("ISO ").append(photo.iso());
        }
        if (photo.focalLength() != null) {
            if (builder.length() > 0) {
                builder.append("  ·  ");
            }
            builder.append(trim(photo.focalLength())).append(" mm");
        }
        return builder.length() == 0 ? "—" : html(builder.toString(), 175);
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static void addInfo(JPanel panel, String label, JLabel value) {
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = panel.getComponentCount() / 2;
        left.anchor = GridBagConstraints.NORTHWEST;
        left.insets = new Insets(0, 0, 6, 8);
        JLabel name = new JLabel(label);
        name.setFont(Theme.font(Font.PLAIN, 11));
        name.setForeground(Theme.MUTED);
        name.setPreferredSize(new Dimension(52, 17));
        panel.add(name, left);

        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = left.gridy;
        right.weightx = 1;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.anchor = GridBagConstraints.NORTHWEST;
        right.insets = new Insets(0, 0, 6, 0);
        panel.add(value, right);
    }

    private static JLabel infoValue() {
        JLabel label = new JLabel("—");
        label.setFont(Theme.font(Font.PLAIN, 12));
        label.setForeground(Theme.TEXT);
        return label;
    }

    private static String html(String text, int width) {
        return "<html><body style='width:" + width + "px'>" + escape(text) + "</body></html>";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String shorten(String text, int length) {
        if (text == null || text.length() <= length) {
            return text == null ? "" : text;
        }
        int side = Math.max(4, (length - 1) / 2);
        return text.substring(0, side) + "…" + text.substring(text.length() - side);
    }

    /**
     * Paints the selected thumbnail with rounded corners and a soft shadow.
     */
    private static final class Preview extends JComponent {

        private final ThumbnailCache cache;

        private Photo photo;

        Preview(ThumbnailCache cache) {
            this.cache = cache;
            setOpaque(false);
        }

        void show(Photo photo) {
            this.photo = photo;
            if (photo != null) {
                cache.request(photo.id(), photo.modifiedAt(), this::repaint);
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                int width = getWidth();
                int height = getHeight();
                int inset = 2;
                Shape frame = new RoundRectangle2D.Float(inset, inset + 2, width - inset * 2, height - inset * 2 - 2, 14, 14);
                g.setColor(new Color(0, 0, 0, 12));
                g.fill(new RoundRectangle2D.Float(inset, inset + 5, width - inset * 2, height - inset * 2 - 2, 14, 14));
                g.setColor(Theme.BACKGROUND);
                g.fill(frame);
                BufferedImage image = photo == null ? null : cache.get(photo.id(), photo.modifiedAt());
                if (image == null) {
                    if (photo != null) {
                        cache.request(photo.id(), photo.modifiedAt(), this::repaint);
                    }
                    Icons.image(46, Theme.FAINT).paintIcon(this, g, width / 2 - 23, height / 2 - 32);
                    g.setFont(Theme.font(Font.PLAIN, 12));
                    g.setColor(Theme.MUTED);
                    String text = photo == null ? "写真を選択" : "読み込み中…";
                    int textWidth = g.getFontMetrics().stringWidth(text);
                    g.drawString(text, (width - textWidth) / 2, height / 2 + 32);
                } else {
                    Shape oldClip = g.getClip();
                    g.setClip(frame);
                    double scale = Math.min((double) (width - 16) / image.getWidth(), (double) (height - 16) / image.getHeight());
                    int w = (int) Math.round(image.getWidth() * scale);
                    int h = (int) Math.round(image.getHeight() * scale);
                    g.drawImage(image, (width - w) / 2, (height - h) / 2, w, h, null);
                    g.setClip(oldClip);
                    if (photo.favorite()) {
                        g.setColor(new Color(255, 255, 255, 230));
                        g.fill(new java.awt.geom.Ellipse2D.Float(width - 40, 10, 28, 28));
                        Icons.star(18, Theme.GOLD, true).paintIcon(this, g, width - 35, 15);
                    }
                }
                g.setColor(Theme.BORDER);
                g.setStroke(new BasicStroke(1f));
                g.draw(frame);
            } finally {
                g.dispose();
            }
        }
    }
}
