import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/** Draws exact PNG exports directly from a puzzle and its verified placements. */
public final class PuzzleRenderer {
    private static final Color BACKGROUND = new Color(0xEEF1E8);
    private static final Color PAPER = new Color(0xFAF8F1);
    private static final Color INK = new Color(0x233E37);
    private static final Color MUTED = new Color(0x687B70);
    private static final Color GRID_LINE = new Color(0xD9DFD5);
    private static final Color DIVIDER = new Color(0xE4E7DD);
    private static final Color BADGE = new Color(0xE1E8DC);
    private static final int CELL = 48;
    private static final int WIDTH = 1100;
    private static final int MARGIN = 32;
    private static final int PANEL_TOP = 145;
    private static final int LEFT_WIDTH = 672;
    private static final int RIGHT_X = 728;
    private static final int RIGHT_WIDTH = 340;
    private static final int GRID_TOP_PADDING = 70;
    private static final int LIST_TOP_PADDING = 108;
    private static final int[] PALETTE = {
        0xE2A04B, 0x579C9A, 0xC87268, 0x9780B8,
        0x658AC1, 0x98A557, 0xBE79A4, 0x5CA985,
        0xCA8762, 0x7F8EBA, 0xBEA44F, 0x78A6AE
    };

    private PuzzleRenderer() {}

    /** Writes output/puzzle.png and output/solution.png; works in headless mode. */
    public static void writeImages(WordSearchGenerator.Puzzle puzzle, Path output)
            throws IOException {
        WordSearchGenerator.validate(puzzle);
        if (output == null) {
            throw new IllegalArgumentException("Не задан каталог для PNG.");
        }
        Files.createDirectories(output);
        writeImage(draw(puzzle, false), output.resolve("puzzle.png"));
        writeImage(draw(puzzle, true), output.resolve("solution.png"));
    }

    private static void writeImage(BufferedImage image, Path path) throws IOException {
        if (!ImageIO.write(image, "png", path.toFile())) {
            throw new IOException("Не удалось записать PNG: " + path);
        }
    }

    private static BufferedImage draw(WordSearchGenerator.Puzzle puzzle, boolean solution) {
        char[][] grid = puzzle.grid();
        List<WordSearchGenerator.Placement> placements = puzzle.placements();
        int rows = grid.length;
        int cols = grid[0].length;
        int rowSpacing = solution ? 46 : 38;
        int gridWidth = cols * CELL;
        int gridHeight = rows * CELL;
        int panelHeight = Math.max(gridHeight + 122,
            LIST_TOP_PADDING + placements.size() * rowSpacing + 20);
        int height = Math.max(850, PANEL_TOP + panelHeight + 48);
        int gridX = MARGIN + (LEFT_WIDTH - gridWidth) / 2;
        int gridY = PANEL_TOP + GRID_TOP_PADDING;

        BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);

            g.setColor(BACKGROUND);
            g.fillRect(0, 0, WIDTH, height);
            text(g, "УЧЕБНЫЙ ПРОЕКТ · ВАРИАНТ 5", 40, 32, 12, Font.BOLD, MUTED);
            text(g, solution ? "Решение" : "Найди слова", 40, 79, 37, Font.BOLD, INK);
            text(g, "Поле " + rows + " × " + cols + "   ·   " + placements.size()
                + " слов   ·   seed " + puzzle.seed(), 42, 111, 14, Font.PLAIN, MUTED);

            g.setColor(PAPER);
            g.fillRoundRect(MARGIN, PANEL_TOP, LEFT_WIDTH, panelHeight, 24, 24);
            g.fillRoundRect(RIGHT_X, PANEL_TOP, RIGHT_WIDTH, panelHeight, 24, 24);

            drawGrid(g, grid, gridX, gridY, solution ? placements : List.of());
            drawWordList(g, placements, solution, rowSpacing);

            int footerY = height - 20;
            text(g, "Русский алфавит: 33 буквы, включая Ё.", 40, footerY,
                12, Font.PLAIN, MUTED);
            String footer = solution ? "Координаты отсчитываются от 1." : "Горизонталь · вертикаль · диагональ";
            g.setFont(font(12, Font.PLAIN));
            int footerX = WIDTH - 40 - g.getFontMetrics().stringWidth(footer);
            text(g, footer, footerX, footerY, 12, Font.PLAIN, MUTED);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void drawGrid(Graphics2D g, char[][] grid, int x, int y,
            List<WordSearchGenerator.Placement> highlights) {
        int rows = grid.length;
        int cols = grid[0].length;
        int width = cols * CELL;
        int height = rows * CELL;

        text(g, "СТОЛБЦЫ", x, y - 40, 10, Font.BOLD, MUTED);
        for (int col = 0; col < cols; col++) {
            centered(g, Integer.toString(col + 1), x + col * CELL + CELL / 2,
                y - 16, 12, Font.PLAIN, MUTED);
        }
        for (int row = 0; row < rows; row++) {
            centered(g, Integer.toString(row + 1), x - 24,
                y + row * CELL + CELL / 2, 12, Font.PLAIN, MUTED);
        }

        g.setColor(GRID_LINE);
        g.setStroke(new BasicStroke(1f));
        for (int col = 0; col <= cols; col++) {
            g.drawLine(x + col * CELL, y, x + col * CELL, y + height);
        }
        for (int row = 0; row <= rows; row++) {
            g.drawLine(x, y + row * CELL, x + width, y + row * CELL);
        }

        // Lines are drawn before letters, using the actual cell centers.
        g.setStroke(new BasicStroke(30f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < highlights.size(); i++) {
            WordSearchGenerator.Placement placement = highlights.get(i);
            int distance = placement.word().length() - 1;
            int endRow = placement.row() + distance * placement.dr();
            int endCol = placement.col() + distance * placement.dc();
            Color color = highlightColor(i);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 92));
            g.drawLine(x + placement.col() * CELL + CELL / 2,
                y + placement.row() * CELL + CELL / 2,
                x + endCol * CELL + CELL / 2,
                y + endRow * CELL + CELL / 2);
        }
        g.setStroke(new BasicStroke(1f));

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                centered(g, Character.toString(grid[row][col]),
                    x + col * CELL + CELL / 2, y + row * CELL + CELL / 2,
                    23, Font.BOLD, INK);
            }
        }
        text(g, "Строки — слева, столбцы — сверху.", x, y + height + 28,
            12, Font.PLAIN, MUTED);
    }

    private static void drawWordList(Graphics2D g,
            List<WordSearchGenerator.Placement> placements, boolean solution, int rowSpacing) {
        int x = RIGHT_X + 24;
        text(g, solution ? "Найденные слова" : "Слова для поиска", x,
            PANEL_TOP + 36, 20, Font.BOLD, INK);
        text(g, solution ? "Цвет показывает положение слова" : "Слова читаются в 8 направлениях",
            x, PANEL_TOP + 59, 12, Font.PLAIN, MUTED);
        if (solution) {
            text(g, "Координаты: строка, столбец", x, PANEL_TOP + 78,
                11, Font.PLAIN, MUTED);
        }
        int firstY = PANEL_TOP + LIST_TOP_PADDING;
        for (int i = 0; i < placements.size(); i++) {
            WordSearchGenerator.Placement placement = placements.get(i);
            int itemY = firstY + i * rowSpacing;
            Color color = solution ? highlightColor(i) : BADGE;
            g.setColor(solution
                ? new Color(color.getRed(), color.getGreen(), color.getBlue(), 65) : color);
            g.fillRoundRect(x, itemY - 15, 28, 28, 10, 10);
            centered(g, Integer.toString(i + 1), x + 14, itemY - 1,
                12, Font.BOLD, INK);

            int wordX = x + 40;
            int maxWidth = RIGHT_WIDTH - 24 - 24 - 40;
            int wordSize = 17;
            g.setFont(font(wordSize, Font.BOLD));
            while (wordSize > 12 && g.getFontMetrics().stringWidth(placement.word()) > maxWidth) {
                g.setFont(font(--wordSize, Font.BOLD));
            }
            text(g, placement.word(), wordX, itemY + 3, wordSize, Font.BOLD, INK);
            if (solution) {
                int distance = placement.word().length() - 1;
                int endRow = placement.row() + distance * placement.dr() + 1;
                int endCol = placement.col() + distance * placement.dc() + 1;
                String coordinates = "(" + (placement.row() + 1) + ", "
                    + (placement.col() + 1) + ") → (" + endRow + ", " + endCol + ")";
                text(g, coordinates, wordX, itemY + 21, 12, Font.PLAIN, MUTED);
            }
            if (i + 1 < placements.size()) {
                g.setColor(DIVIDER);
                int dividerY = itemY + rowSpacing - 20;
                g.drawLine(wordX, dividerY, RIGHT_X + RIGHT_WIDTH - 24, dividerY);
            }
        }
    }

    private static Font font(int size, int style) {
        return new Font(Font.SANS_SERIF, style, size);
    }

    private static void text(Graphics2D g, String value, int x, int baseline,
            int size, int style, Color color) {
        g.setFont(font(size, style));
        g.setColor(color);
        g.drawString(value, x, baseline);
    }

    private static void centered(Graphics2D g, String value, int centerX, int centerY,
            int size, int style, Color color) {
        g.setFont(font(size, style));
        g.setColor(color);
        FontMetrics metrics = g.getFontMetrics();
        float x = centerX - metrics.stringWidth(value) / 2f;
        float baseline = centerY + (metrics.getAscent() - metrics.getDescent()) / 2f;
        g.drawString(value, x, baseline);
    }

    private static Color highlightColor(int index) {
        if (index < PALETTE.length) {
            return new Color(PALETTE[index]);
        }
        float hue = (float) ((index * 0.6180339887498949) % 1.0);
        return Color.getHSBColor(hue, 0.47f, 0.72f);
    }
}
