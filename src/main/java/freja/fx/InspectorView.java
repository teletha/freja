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

import javafx.geometry.Pos;
import kiss.Variable;
import stylist.Style;
import stylist.StyleDSL;
import freja.model.Photo;
import viewtify.Viewtify;
import viewtify.keys.Key;
import viewtify.ui.UIButton;
import viewtify.ui.UIHBox;
import viewtify.ui.UIImage;
import viewtify.ui.UILabel;
import viewtify.ui.UIScrollPane;
import viewtify.ui.UITextArea;
import viewtify.ui.UIVBox;
import viewtify.ui.View;
import viewtify.ui.ViewDSL;
import viewtify.ui.helper.User;

/**
 * The details panel docked beside the grid: preview, EXIF summary, favorite switch, star rating
 * and caption editing. The Viewtify counterpart of the Swing {@code PhotoInspector}.
 */
public final class InspectorView extends View {

    /** User actions initiated from the inspector. */
    public interface Listener {

        default void onOpen(Photo photo) {
        }

        default void onReveal(Photo photo) {
        }

        default void onFavorite(Photo photo, boolean favorite) {
        }

        default void onRating(Photo photo, int rating) {
        }

        default void onCaption(Photo photo, String caption) {
        }
    }

    private UIVBox body;

    private UIScrollPane scroll;

    private UIImage preview;

    private UILabel fileName;

    private UILabel folderName;

    private UILabel dateValue;

    private UILabel dimensionsValue;

    private UILabel sizeValue;

    private UILabel cameraValue;

    private UILabel lensValue;

    private UILabel exposureValue;

    private UILabel ratingLabel;

    private UIHBox ratingRow;

    private UILabel captionTitle;

    private UITextArea caption;

    private UIButton open;

    private UIButton favorite;

    private UIButton saveCaption;

    private UIButton reveal;

    private UIButton[] stars = new UIButton[5];

    /** The thumbnail source, injected by the window. */
    private ThumbnailStore thumbs;

    private Listener listener = new Listener() {
    };

    private Photo photo;

    private boolean updating;

    public void setListener(Listener listener) {
        this.listener = listener == null ? this.listener : listener;
    }

    public void setThumbnails(ThumbnailStore thumbs) {
        this.thumbs = thumbs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ViewDSL declareUI() {
        return new ViewDSL() {
            {
                $(scroll, style.panel, () -> {
                    $(body, style.body, () -> {
                        $(preview, style.preview);
                        $(fileName, style.fileName);
                        $(folderName, style.folder);
                        $(hbox, style.actions, () -> {
                            $(open, style.primary);
                            $(favorite);
                        });
                        $(ratingRow, style.rating, () -> {
                            $(ratingLabel, style.infoLabel);
                            for (UIButton star : stars) {
                                $(star, style.star);
                            }
                        });
                        $(hsep);
                        $(vbox, style.info, () -> {
                            form(en("Capture"), dateValue);
                            form(en("Dimensions"), dimensionsValue);
                            form(en("Size"), sizeValue);
                            form(en("Camera"), cameraValue);
                            form(en("Lens"), lensValue);
                            form(en("Exposure"), exposureValue);
                        });
                        $(captionTitle, style.captionTitle);
                        $(caption, style.caption);
                        $(hbox, style.footer, () -> {
                            $(saveCaption);
                            $(reveal);
                        });
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
        ratingLabel.text(en("Rating"));
        captionTitle.text(en("Caption"));
        open.text(en("Open"));
        favorite.text(en("Favorite"));
        saveCaption.text(en("Save"));
        reveal.text(en("Open in Explorer"));
        saveCaption.style(style.small);
        reveal.style(style.link);

        fileName.ui().setWrapText(true);
        preview.ui().setPreserveRatio(true);
        preview.ui().setSmooth(true);
        preview.ui().setFitWidth(260);
        caption.autoWrap(true);
        caption.ui().setPrefRowCount(3);
        body.ui().setSpacing(6);
        ratingRow.ui().setAlignment(Pos.CENTER_LEFT);
        saveCaption.visible(false).managed(false);

        open.when(User.Action, event -> {
            if (photo != null) {
                listener.onOpen(photo);
            }
        });
        favorite.when(User.Action, event -> {
            if (photo == null) {
                return;
            }
            Photo updated = photo.withFavorite(!photo.favorite());
            show(updated);
            listener.onFavorite(updated, updated.favorite());
        });
        reveal.when(User.Action, event -> {
            if (photo != null) {
                listener.onReveal(photo);
            }
        });
        saveCaption.when(User.Action, event -> commitCaption());
        caption.keybind(Key.Enter.ctrl(), () -> commitCaption());
        caption.whenUnfocus(() -> {
            if (saveCaption.isEnable()) {
                commitCaption();
            }
        });
        caption.observe().to(text -> {
            if (updating || photo == null) {
                return;
            }
            String current = photo.caption() == null ? "" : photo.caption();
            boolean changed = !current.equals(text == null ? "" : text.trim());
            saveCaption.visible(changed).managed(changed);
        });
        for (int i = 0; i < stars.length; i++) {
            int value = i + 1;
            stars[i].tooltip(en("{0} stars", value));
            stars[i].when(User.Action, event -> {
                if (photo == null) {
                    return;
                }
                int rating = photo.rating() == value ? 0 : value;
                Photo updated = photo.withRating(rating);
                show(updated);
                listener.onRating(updated, rating);
            });
        }
        show(null);
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
                Variable<String> hint = en("Select a photo");
                fileName.text(hint);
                fileName.style(style.placeholder);
                folderName.text(" ");
                dateValue.text("—");
                dimensionsValue.text("—");
                sizeValue.text("—");
                cameraValue.text("—");
                lensValue.text("—");
                exposureValue.text("—");
                caption.value("");
                caption.disable(true);
                open.disable(true);
                favorite.disable(true);
                reveal.visible(false).managed(false);
                favorite.text(en("Favorite"));
                updateStars(0, false);
                saveCaption.visible(false).managed(false);
                return;
            }
            fileName.text(photo.fileName());
            fileName.unstyle(style.placeholder);
            folderName.text(shorten(photo.folderPath(), 46));
            folderName.tooltip(photo.folderPath());
            dateValue.text(photo.capturedLabel());
            dimensionsValue.text(photo.dimensionsLabel() + "  ·  " + photo.formatLabel());
            sizeValue.text(photo.sizeLabel());
            cameraValue.text(photo.cameraLabel() == null ? "—" : photo.cameraLabel());
            lensValue.text(photo.lens() == null ? "—" : photo.lens());
            exposureValue.text(exposure(photo));
            caption.enable(true);
            caption.value(photo.caption() == null ? "" : photo.caption());
            open.enable(true);
            favorite.enable(true);
            reveal.visible(true).managed(true);
            favorite.text(photo.favorite() ? en("Favorited") : en("Favorite"));
            updateStars(photo.rating(), true);
            saveCaption.visible(false).managed(false);
            if (thumbs != null) {
                thumbs.imageFor(photo).observe().on(Viewtify.UIThread).to(image -> {
                    if (image != null) {
                        preview.value(image);
                    }
                });
            }
        } finally {
            updating = false;
        }
    }

    private void commitCaption() {
        if (photo == null) {
            return;
        }
        String value = caption.value() == null ? "" : caption.value().trim();
        Photo updated = photo.withCaption(value.isEmpty() ? null : value);
        show(updated);
        listener.onCaption(updated, value);
    }

    /** Paints the rating row; inactive photos gray the stars out. */
    private void updateStars(int rating, boolean enabled) {
        for (int i = 0; i < stars.length; i++) {
            boolean filled = enabled && i < rating;
            stars[i].text(filled ? "★" : "☆");
            stars[i].ui().setStyle("-fx-text-fill: " + (filled ? "#F1B64A" : enabled ? "#8B929C" : "#4A4F57") + ";");
            stars[i].enable(enabled);
        }
    }

    /** Assembles the exposure line from the stored EXIF values. */
    private String exposure(Photo photo) {
        StringBuilder builder = new StringBuilder();
        if (photo.exposure() != null) {
            builder.append(photo.exposure());
        }
        if (photo.aperture() != null) {
            join(builder).append("f/").append(trim(photo.aperture()));
        }
        if (photo.iso() != null) {
            join(builder).append("ISO ").append(photo.iso());
        }
        if (photo.focalLength() != null) {
            join(builder).append(trim(photo.focalLength())).append(" mm");
        }
        return builder.length() == 0 ? "—" : builder.toString();
    }

    private static StringBuilder join(StringBuilder builder) {
        if (builder.length() > 0) {
            builder.append("  ·  ");
        }
        return builder;
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static String shorten(String text, int length) {
        if (text == null || text.length() <= length) {
            return text == null ? "" : text;
        }
        int side = Math.max(4, (length - 1) / 2);
        return text.substring(0, side) + "…" + text.substring(text.length() - side);
    }

    /** Visual language of the inspector. */
    interface style extends StyleDSL {

        Style panel = () -> background.color("derive(-fx-base, 2%)");

        Style body = () -> padding.vertical(14, px).horizontal(16, px);

        Style preview = () -> {
            display.maxWidth(280, px);
            margin.bottom(6, px);
        };

        Style fileName = () -> font.size(15, px).weight(700);

        Style placeholder = () -> font.color("-fx-mid-text-color");

        Style folder = () -> {
            font.size(11, px).color("-fx-mid-text-color");
            margin.bottom(6, px);
        };

        Style actions = () -> display.width(360, px);

        Style primary = () -> background.color("-fx-accent");

        Style rating = () -> padding.vertical(4, px);

        Style star = () -> {
            background.color("transparent");
            border.none();
            font.size(16, px);
            padding.vertical(1, px).horizontal(4, px);
            cursor.pointer();
        };

        Style info = () -> padding.vertical(6, px);

        Style infoLabel = () -> font.size(11, px).color("-fx-mid-text-color");

        Style captionTitle = () -> {
            font.size(11, px).weight(700).color("-fx-mid-text-color");
            padding.vertical(6, px);
        };

        Style caption = () -> display.height(80, px);

        Style footer = () -> display.width(360, px);

        Style small = () -> font.size(11, px);

        Style link = () -> {
            background.color("transparent");
            border.none();
            font.color("derive(-fx-accent, 8%)");
            cursor.pointer();
            $.hover(() -> font.color("derive(-fx-accent, 15%)"));
        };
    }
}
