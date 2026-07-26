package com.betterlist.gui;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for the material-list screen geometry.
 *
 * <p>All values are GUI-scaled coordinates. Keeping the responsive decisions here prevents
 * button placement, rendering and hit testing from drifting apart.</p>
 */
record LayoutMetrics(
        int contentX,
        int contentWidth,
        int headerY,
        int listX,
        int listY,
        int listWidth,
        int listHeight,
        GuiBetterMaterialList.LayoutMode effectiveMode,
        boolean compactColumns,
        boolean responsiveSingleFallback,
        Map<Control, Position> controls) {

    private static final int SCREEN_MARGIN = 10;
    private static final int EDGE_Y = 6;
    private static final int CONTROL_HEIGHT = 20;
    private static final int CONTROL_GAP = 4;
    private static final int ROW_GAP = 4;
    private static final int HEADER_GAP = 8;
    private static final int HEADER_HEIGHT = 12;
    private static final int LIST_BOTTOM_GAP = 4;

    // WidgetListBase reserves 3 px on the left and 14 px overall for padding/scrollbar.
    private static final int LIST_ENTRY_LEFT_INSET = 3;
    private static final int LIST_ENTRY_WIDTH_TRIM = 14;

    // Full-width columns fit comfortably at 620 px. Between 480 and 619 px a compact
    // column profile keeps both requested columns usable instead of silently disabling
    // the layout switch. Only truly narrower screens fall back to one column.
    private static final int FULL_TWO_COLUMN_WIDTH = 620;
    private static final int MIN_COMPACT_TWO_COLUMN_WIDTH = 480;
    private static final int PREFERRED_MIN_WIDTH = 600;

    private static final int COMPACT_CHECKBOX_MARGIN = 4;
    private static final int COMPACT_COLUMN_GAP = 3;
    private static final int COMPACT_MISSING_WIDTH = 35;
    private static final int COMPACT_AVAILABLE_WIDTH = 42;
    private static final int COMPACT_PLACED_WIDTH = 42;
    private static final int COMPACT_TOTAL_WIDTH = 38;

    enum Control {
        PARTY(78),
        LAYOUT(54),
        AUTO_REFRESH(60),
        REFRESH(20),
        CHESTS(68),
        SCHEMATICS(90),
        SETTINGS(62),
        CACHE(60),
        SEARCH(120),
        PLACED_FILTER(54),
        STORED_FILTER(54),
        CHECKED_FILTER(68),
        FOCUS_MODE(82),
        PLAYERS(70),
        CLEAR_TARGETS(62);

        private final int width;

        Control(int width) {
            this.width = width;
        }

        int width() {
            return this.width;
        }
    }

    record Position(int x, int y) {}

    record Section(int x, int width) {}

    record Columns(
            int nameStart,
            int totalStart,
            int placedStart,
            int availableStart,
            int missingStart,
            int checkboxStart,
            int checkboxEnd,
            int maxNameWidth) {}

    static LayoutMetrics calculate(
            int screenWidth,
            int screenHeight,
            GuiBetterMaterialList.LayoutMode requestedMode,
            boolean includeFocusControls) {
        int availableWidth = Math.max(1, screenWidth - SCREEN_MARGIN * 2);
        int preferredWidth = Math.max(PREFERRED_MIN_WIDTH, (int) (screenWidth * 0.9));
        int rawWidth = Math.min(preferredWidth, availableWidth);

        boolean responsiveSingleFallback =
                requestedMode != GuiBetterMaterialList.LayoutMode.SINGLE
                        && rawWidth < MIN_COMPACT_TWO_COLUMN_WIDTH;
        GuiBetterMaterialList.LayoutMode effectiveMode = responsiveSingleFallback
                ? GuiBetterMaterialList.LayoutMode.SINGLE
                : requestedMode;
        boolean compactColumns =
                effectiveMode != GuiBetterMaterialList.LayoutMode.SINGLE
                        && rawWidth < FULL_TWO_COLUMN_WIDTH;

        int contentWidth = effectiveMode == GuiBetterMaterialList.LayoutMode.SINGLE
                ? Math.min(rawWidth, BmlLayoutConstants.SINGLE_MODE_MAX_WIDTH)
                : rawWidth;
        int contentX = (screenWidth - contentWidth) / 2;

        EnumMap<Control, Position> positions = new EnumMap<>(Control.class);
        int topBottom = layoutTopControls(positions, contentX, contentWidth);
        int bottomTop = layoutBottomControls(
                positions, contentX, contentWidth, screenHeight, includeFocusControls);

        int headerY = topBottom + HEADER_GAP;
        int listY = headerY + HEADER_HEIGHT;
        int listBottom = bottomTop - LIST_BOTTOM_GAP;
        int listHeight = Math.max(0, listBottom - listY);

        return new LayoutMetrics(
                contentX,
                contentWidth,
                headerY,
                contentX,
                listY,
                contentWidth,
                listHeight,
                effectiveMode,
                compactColumns,
                responsiveSingleFallback,
                Map.copyOf(positions));
    }

    Position control(Control control) {
        Position position = this.controls.get(control);
        if (position == null) {
            throw new IllegalArgumentException("Control is not part of this layout: " + control);
        }
        return position;
    }

    boolean isSingleColumn() {
        return this.effectiveMode == GuiBetterMaterialList.LayoutMode.SINGLE;
    }

    int entryAreaX() {
        return this.listX + LIST_ENTRY_LEFT_INSET;
    }

    int entryAreaWidth() {
        return Math.max(0, this.listWidth - LIST_ENTRY_WIDTH_TRIM);
    }

    Section entrySection(int index) {
        return section(this.entryAreaX(), this.entryAreaWidth(), this.effectiveMode, index);
    }

    static Section section(
            int x,
            int width,
            GuiBetterMaterialList.LayoutMode mode,
            int index) {
        if (mode == GuiBetterMaterialList.LayoutMode.SINGLE || index == 0) {
            int sectionWidth = mode == GuiBetterMaterialList.LayoutMode.SINGLE ? width : width / 2;
            return new Section(x, sectionWidth);
        }

        int halfWidth = width / 2;
        return new Section(x + halfWidth + 1, Math.max(0, width - halfWidth - 1));
    }

    Columns columns(int x, int width) {
        boolean singleColumn = isSingleColumn();
        int checkboxMargin = this.compactColumns
                ? COMPACT_CHECKBOX_MARGIN
                : BmlLayoutConstants.CHECKBOX_MARGIN;
        int columnGap = this.compactColumns
                ? COMPACT_COLUMN_GAP
                : BmlLayoutConstants.COLUMN_GAP;
        int missingWidth = this.compactColumns
                ? COMPACT_MISSING_WIDTH
                : BmlLayoutConstants.MISSING_WIDTH;
        int availableWidth = this.compactColumns
                ? COMPACT_AVAILABLE_WIDTH
                : BmlLayoutConstants.AVAILABLE_WIDTH;
        int placedWidth = this.compactColumns
                ? COMPACT_PLACED_WIDTH
                : BmlLayoutConstants.PLACED_WIDTH;
        int totalWidth = singleColumn
                ? BmlLayoutConstants.SINGLE_TOTAL_WIDTH
                : (this.compactColumns ? COMPACT_TOTAL_WIDTH : BmlLayoutConstants.TOTAL_WIDTH);

        int checkboxEnd = x + width - checkboxMargin;
        int checkboxStart = checkboxEnd - BmlLayoutConstants.CHECKBOX_WIDTH;
        int missingEnd = checkboxStart - columnGap;
        int missingStart = missingEnd - missingWidth;
        int availableEnd = missingStart - columnGap;
        int availableStart = availableEnd - availableWidth;
        int placedEnd = availableStart - columnGap;
        int placedStart = placedEnd - placedWidth;
        int totalEnd = placedStart - columnGap;
        int totalStart = totalEnd - totalWidth;
        int nameStart = x + BmlLayoutConstants.NAME_OFFSET_X;

        return new Columns(
                nameStart,
                totalStart,
                placedStart,
                availableStart,
                missingStart,
                checkboxStart,
                checkboxEnd,
                totalStart - nameStart - columnGap);
    }

    private static int layoutTopControls(
            EnumMap<Control, Position> positions,
            int contentX,
            int contentWidth) {
        List<Control> topControls = List.of(
                Control.PARTY,
                Control.LAYOUT,
                Control.AUTO_REFRESH,
                Control.REFRESH,
                Control.CHESTS,
                Control.SCHEMATICS,
                Control.SETTINGS,
                Control.CACHE);

        int index = 0;
        int firstRowX = 0;
        while (index < topControls.size()) {
            Control control = topControls.get(index);
            if (firstRowX > 0 && firstRowX + control.width() > contentWidth) {
                break;
            }

            positions.put(control, new Position(contentX + firstRowX, EDGE_Y));
            firstRowX += control.width() + CONTROL_GAP;
            index++;
        }

        // Search always owns the left side of the second top row. Any whole button
        // that did not fit above continues in the remaining space on this row.
        int secondRowY = EDGE_Y + CONTROL_HEIGHT + ROW_GAP;
        positions.put(Control.SEARCH, new Position(contentX, secondRowY));

        int overflowBottom = placeTopOverflowRightAligned(
                positions,
                topControls.subList(index, topControls.size()),
                contentX,
                contentWidth,
                secondRowY);

        return Math.max(secondRowY + CONTROL_HEIGHT, overflowBottom);
    }

    /**
     * Places wrapped top-bar controls against the right edge. The first overflow
     * row reserves the left side for search; later rows may use the full width.
     */
    private static int placeTopOverflowRightAligned(
            EnumMap<Control, Position> positions,
            List<Control> controls,
            int contentX,
            int contentWidth,
            int firstRowY) {
        int index = 0;
        int y = firstRowY;

        while (index < controls.size()) {
            int rowStart = index;
            int reservedLeft = y == firstRowY ? Control.SEARCH.width() + CONTROL_GAP : 0;
            int availableWidth = Math.max(0, contentWidth - reservedLeft);
            int rowWidth = 0;

            while (index < controls.size()) {
                Control control = controls.get(index);
                int candidateWidth = rowWidth == 0
                        ? control.width()
                        : rowWidth + CONTROL_GAP + control.width();
                if (rowWidth > 0 && candidateWidth > availableWidth) {
                    break;
                }
                if (rowWidth == 0 && candidateWidth > availableWidth && reservedLeft > 0) {
                    break;
                }

                rowWidth = candidateWidth;
                index++;
            }

            // A control that cannot share the search row starts on the next row.
            if (index == rowStart) {
                y += CONTROL_HEIGHT + ROW_GAP;
                continue;
            }

            int x = contentWidth - rowWidth;
            for (int i = rowStart; i < index; i++) {
                Control control = controls.get(i);
                positions.put(control, new Position(contentX + x, y));
                x += control.width() + CONTROL_GAP;
            }

            if (index < controls.size()) {
                y += CONTROL_HEIGHT + ROW_GAP;
            }
        }

        return y + CONTROL_HEIGHT;
    }

    private static int layoutBottomControls(
            EnumMap<Control, Position> positions,
            int contentX,
            int contentWidth,
            int screenHeight,
            boolean includeFocusControls) {
        List<Control> filters = includeFocusControls
                ? List.of(
                        Control.PLACED_FILTER,
                        Control.STORED_FILTER,
                        Control.CHECKED_FILTER,
                        Control.FOCUS_MODE,
                        Control.PLAYERS,
                        Control.CLEAR_TARGETS)
                : List.of(
                        Control.PLACED_FILTER,
                        Control.STORED_FILTER,
                        Control.CHECKED_FILTER);

        EnumMap<Control, Position> local = new EnumMap<>(Control.class);
        int blockBottom;

        if (rowWidth(filters) <= contentWidth) {
            placeSingleRow(local, filters, 0, 0);
            blockBottom = CONTROL_HEIGHT;
        } else {
            blockBottom = placeFlow(local, filters, 0, 0, contentWidth);
        }

        int bottomTop = Math.max(EDGE_Y, screenHeight - EDGE_Y - blockBottom);
        for (Map.Entry<Control, Position> entry : local.entrySet()) {
            Position localPosition = entry.getValue();
            positions.put(entry.getKey(),
                    new Position(contentX + localPosition.x(), bottomTop + localPosition.y()));
        }
        return bottomTop;
    }

    private static int placeFlow(
            EnumMap<Control, Position> positions,
            List<Control> controls,
            int startX,
            int startY,
            int availableWidth) {
        int x = 0;
        int y = startY;

        for (Control control : controls) {
            if (x > 0 && x + control.width() > availableWidth) {
                x = 0;
                y += CONTROL_HEIGHT + ROW_GAP;
            }
            positions.put(control, new Position(startX + x, y));
            x += control.width() + CONTROL_GAP;
        }

        return y + CONTROL_HEIGHT;
    }

    private static void placeSingleRow(
            EnumMap<Control, Position> positions,
            List<Control> controls,
            int startX,
            int y) {
        int x = startX;
        for (Control control : controls) {
            positions.put(control, new Position(x, y));
            x += control.width() + CONTROL_GAP;
        }
    }

    private static int rowWidth(List<Control> controls) {
        int width = 0;
        for (int i = 0; i < controls.size(); i++) {
            if (i > 0) width += CONTROL_GAP;
            width += controls.get(i).width();
        }
        return width;
    }
}
