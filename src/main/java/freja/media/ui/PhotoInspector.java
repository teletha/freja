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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import com.formdev.flatlaf.FlatClientProperties;

import freja.Icons;
import freja.Theme;
import freja.media.Photo;

import laevatain.api.ComponentHelper;
import laevatain.component.ARXLabel;
import laevatain.component.ARXPanel;
import laevatain.component.ARXScrollPane;

/**
 * The details panel shown next to the thumbnail grid.
 */
public final class PhotoInspector extends JPanel implements ComponentHelper<PhotoInspector> {
    /** User actions initiated from the inspector. */
    public interface Listener {
        void onOpen(Photo photo);
        void onReveal(Photo photo);
        void onFavorite(Photo photo, boolean favorite);
        void onRating(Photo photo, int rating);
        void onCaption(Photo photo, String caption);
    }
    private final JLabel fileName = new ARXLabel()
            .font(Theme.font(Font.BOLD, 16)).foreground(Theme.TEXT).alignmentX(LEFT_ALIGNMENT);
    private final JLabel folderName = new ARXLabel()
            .font(Theme.font(Font.PLAIN, 11)).foreground(Theme.MUTED).alignmentX(LEFT_ALIGNMENT);
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
    public PhotoInspector() {
        layout(new BorderLayout()).background(Theme.SURFACE).preferredSize(308, 600)
                .border(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.BORDER));
        JPanel body = new ARXPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Theme.SURFACE);
        body.setBorder(BorderFactory.createEmptyBorder(14, 16, 18, 16));
        body.add(fileName);
        body.add(Box.createVerticalStrut(4));
        body.add(folderName);
        body.add(Box.createVerticalStrut(12));
        JPanel actions = new ARXPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        open.putClientProperty(FlatClientProperties.STYLE, "background:#2E6BE6; foreground:#FFFFFF; borderWidth:0; hoverBackground:#1F52B8; pressedBackground:#1A459B");
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
        JPanel rating = new ARXPanel(new FlowLayout(FlowLayout.LEFT, 1, 0));
        rating.setOpaque(false);
        rating.setAlignmentX(LEFT_ALIGNMENT);
        JLabel ratingLabel = new ARXLabel("評価");
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
        JPanel info = new ARXPanel(new GridBagLayout());
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
        JLabel captionLabel = new ARXLabel("キャプション");
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
        JPanel footer = new ARXPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
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
        JScrollPane scroll = new ARXScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
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
                String duration = photo.durationLabel().isEmpty() ? "" : photo.durationLabel() + "  ·  ";
                dimensionsValue.setText(duration + photo.dimensionsLabel() + "  ·  " + photo.formatLabel());
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
        JLabel name = new ARXLabel(label);
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
        return new ARXLabel("—").font(Theme.font(Font.PLAIN, 12)).foreground(Theme.TEXT);
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
}