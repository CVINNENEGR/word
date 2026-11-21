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

/**
 * Рисует два PNG-экспорта пазла через стандартные AWT/ImageIO.
 *
 * <p>Почему AWT, а не, скажем, SVG или HTML-скриншот:
 * <ul>
 *   <li>Задание требует скриншоты результата; PNG — самый простой формат,
 *       который можно приложить к README и открыть без браузера.</li>
 *   <li>AWT и ImageIO входят в JDK, поэтому не нужны сторонние библиотеки
 *       и не нужен графический сервер: класс работает в headless-режиме
 *       (см. {@code -Djava.awt.headless=true}).</li>
 *   <li>Рисование напрямую из {@code Puzzle} гарантирует, что картинка
 *       соответствует данным, а не является скриншотом чужой страницы.</li>
 * </ul>
 *
 * <p>Рендерер намеренно НЕ доверяет вызывающему: перед рисованием он сам
 * вызывает {@link WordSearchGenerator#proverka_correctnosti}, чтобы не
 * экспортировать битый пазл.
 */
public final class PuzzleRenderer {
    // Цвета подобраны так, чтобы картинка хорошо читалась и в печати, и на
    // экране: светлый фон, тёмные чернила, приглушённые линии сетки.
    private static final Color BACKGROUND = new Color(0xEEF1E8);
    private static final Color PAPER = new Color(0xFAF8F1);
    private static final Color INK = new Color(0x233E37);
    private static final Color MUTED = new Color(0x687B70);
    private static final Color GRID_LINE = new Color(0xD9DFD5);
    private static final Color DIVIDER = new Color(0xE4E7DD);
    private static final Color BADGE = new Color(0xE1E8DC);

    // Размеры зафиксированы под поддерживаемые сетки (до 12×10).
    // 48 px на клетку даёт читаемые буквы 23 px даже на 10-дюймовом экране.
    private static final int CELL = 48;
    private static final int WIDTH = 1100;
    private static final int MARGIN = 32;
    private static final int PANEL_TOP = 145;
    private static final int LEFT_WIDTH = 672;
    private static final int RIGHT_X = 728;
    private static final int RIGHT_WIDTH = 340;
    private static final int GRID_TOP_PADDING = 70;
    private static final int LIST_TOP_PADDING = 108;

    // Палитра на 12 цветов. Если слов больше, highlightColor генерирует
    // дополнительные оттенки через золотое сечение в HSB — так соседние
    // цвета не сливаются.
    private static final int[] PALETTE = {
        0xE2A04B, 0x579C9A, 0xC87268, 0x9780B8,
        0x658AC1, 0x98A557, 0xBE79A4, 0x5CA985,
        0xCA8762, 0x7F8EBA, 0xBEA44F, 0x78A6AE
    };

    private PuzzleRenderer() {}

    /**
     * Валидирует пазл и записывает puzzle.png и solution.png.
     *
     * <p>Два PNG, а не один: задание требует показать и «чистую» сетку,
     * и решение. Один файл с флагом «показать/скрыть» невозможен, а два
     * файла удобно вставлять в README рядом.
     *
     * <p>Валидация здесь — защита от вызова рендерера на частичном результате:
     * если пазл невалиден, лучше упасть с понятной ошибкой, чем нарисовать
     * картинку, которая не соответствует правилам.
     */
    public static void writeImages(WordSearchGenerator.Puzzle puzzle, Path output) throws IOException {
        WordSearchGenerator.proverka_correctnosti(puzzle);
        if (output == null) throw new IllegalArgumentException("Не задан каталог для PNG.");
        Files.createDirectories(output);
        for (boolean solution : new boolean[]{false, true}) {
            BufferedImage image = draw(puzzle, solution);
            Path path = output.resolve(solution ? "solution.png" : "puzzle.png");
            if (!ImageIO.write(image, "png", path.toFile())) {
                throw new IOException("Не удалось записать PNG: " + path);
            }
        }
    }

    /**
     * Рисует один холст: фон, заголовок, две карточки (сетка + список слов),
     * футер. Флаг {@code solution} управляет только подсветкой и подписями —
     * геометрия остаётся той же, чтобы puzzle.png и solution.png было удобно
     * сравнивать бок о бок.
     */
    private static BufferedImage draw(WordSearchGenerator.Puzzle puzzle, boolean solution) {
        char[][] grid = puzzle.grid();
        List<WordSearchGenerator.Placement> placements = puzzle.placements();
        int rows = grid.length, cols = grid[0].length;
        // В режиме решения под каждым словом есть строка координат, поэтому
        // межстрочный интервал больше.
        int rowSpacing = solution ? 46 : 38;
        int gridWidth = cols * CELL, gridHeight = rows * CELL;
        // Высота правой карточки зависит от числа слов; левая — от сетки.
        // Берём максимум, чтобы карточки были одинаковой высоты.
        int panelHeight = Math.max(gridHeight + 122, LIST_TOP_PADDING + placements.size() * rowSpacing + 20);
        int height = Math.max(850, PANEL_TOP + panelHeight + 48);
        // Сетка центрируется по левой карточке, а не прижимается к краю:
        // для 10×10 остаются симметричные поля, для 12×10 — меньше, но
        // тоже симметрично.
        int gridX = MARGIN + (LEFT_WIDTH - gridWidth) / 2;
        int gridY = PANEL_TOP + GRID_TOP_PADDING;

        // TYPE_INT_RGB без альфа-канала: PNG получается меньше, а прозрачность
        // здесь не нужна (фон заливается сплошным цветом).
        BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            // Антиалиасинг для линий и текста, дробные метрики — чтобы буквы
            // не «прыгали» при центрировании. Эти подсказки заметно влияют
            // на качество картинки при экспорте.
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            g.setColor(BACKGROUND); g.fillRect(0, 0, WIDTH, height);
            drawText(g, "УЧЕБНЫЙ ПРОЕКТ · ВАРИАНТ 5", 40, 32, 12, Font.BOLD, MUTED);
            drawText(g, solution ? "Решение" : "Найди слова", 40, 79, 37, Font.BOLD, INK);
            drawText(g, "Поле " + rows + " × " + cols + "   ·   " + placements.size()
                + " слов   ·   seed " + puzzle.seed(), 42, 111, 14, Font.PLAIN, MUTED);

            // Две карточки-подложки: визуально разделяют сетку и список слов,
            // чтобы глаз не «плавал» по пустому фону.
            g.setColor(PAPER);
            g.fillRoundRect(MARGIN, PANEL_TOP, LEFT_WIDTH, panelHeight, 24, 24);
            g.fillRoundRect(RIGHT_X, PANEL_TOP, RIGHT_WIDTH, panelHeight, 24, 24);

            // В puzzle.png подсветки нет (пустой список), в solution.png — есть.
            // Один и тот же drawGrid обслуживает оба режима, чтобы геометрия
            // сетки гарантированно совпадала.
            drawGrid(g, grid, gridX, gridY, solution ? placements : List.of());
            drawWordList(g, placements, solution, rowSpacing);

            // Футер: слева — напоминание про алфавит, справа — режим картинки.
            // Правый край прижимаем через FontMetrics.stringWidth, иначе
            // пришлось бы угадывать отступ.
            int footerY = height - 20;
            drawText(g, "Русский алфавит: 33 буквы, включая Ё.", 40, footerY, 12, Font.PLAIN, MUTED);
            String footer = solution ? "Координаты отсчитываются от 1." : "Горизонталь · вертикаль · диагональ";
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            int footerX = WIDTH - 40 - g.getFontMetrics().stringWidth(footer);
            drawText(g, footer, footerX, footerY, 12, Font.PLAIN, MUTED);
        } finally {
            // Graphics2D держит нативные ресурсы; без dispose() они утекают,
            // особенно заметно при серийной генерации в цикле.
            g.dispose();
        }
        return image;
    }

    /**
     * Рисует номера строк/столбцов, линии сетки, подсветку (если есть) и буквы.
     *
     * <p>Порядок важен: подсветка рисуется ДО букв. Если рисовать после,
     * полупрозрачная полоса «закрасит» буквы и они станут плохо читаться.
     * При alpha≈92 буквы остаются чёткими, а полоса видна.
     */
    private static void drawGrid(Graphics2D g, char[][] grid, int x, int y,
            List<WordSearchGenerator.Placement> highlights) {
        int rows = grid.length, cols = grid[0].length;
        int width = cols * CELL, height = rows * CELL;

        drawText(g, "СТОЛБЦЫ", x, y - 40, 10, Font.BOLD, MUTED);
        for (int col = 0; col < cols; col++) {
            drawCentered(g, Integer.toString(col + 1), x + col * CELL + CELL / 2, y - 16, 12, Font.PLAIN, MUTED);
        }
        for (int row = 0; row < rows; row++) {
            drawCentered(g, Integer.toString(row + 1), x - 24, y + row * CELL + CELL / 2, 12, Font.PLAIN, MUTED);
        }
        // Линии сетки рисуем тонким штрихом; цвет GRID_LINE подобран так, чтобы
        // не перебивать буквы и подсветку.
        g.setColor(GRID_LINE); g.setStroke(new BasicStroke(1f));
        for (int col = 0; col <= cols; col++) g.drawLine(x + col * CELL, y, x + col * CELL, y + height);
        for (int row = 0; row <= rows; row++) g.drawLine(x, y + row * CELL, x + width, y + row * CELL);

        // Подсветка: толстая линия (30 px) с CAP_ROUND, чтобы она не выглядела
        // обрубленной на концах. Координаты — центры клеток, а не углы:
        // так линия проходит ровно через буквы.
        g.setStroke(new BasicStroke(30f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < highlights.size(); i++) {
            WordSearchGenerator.Placement p = highlights.get(i);
            int distance = p.word().length() - 1;
            int endRow = p.row() + distance * p.dr();
            int endCol = p.col() + distance * p.dc();
            Color color = highlightColor(i);
            // alpha=92 — компромисс: полоса заметна, но буквы под ней читаются.
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 92));
            g.drawLine(x + p.col() * CELL + CELL / 2, y + p.row() * CELL + CELL / 2,
                x + endCol * CELL + CELL / 2, y + endRow * CELL + CELL / 2);
        }
        g.setStroke(new BasicStroke(1f));

        // Буквы поверх подсветки: drawCentered сам считает baseline по метрикам,
        // поэтому буквы визуально центрированы в клетке независимо от шрифта.
        for (int row = 0; row < rows; row++)
            for (int col = 0; col < cols; col++)
                drawCentered(g, Character.toString(grid[row][col]),
                    x + col * CELL + CELL / 2, y + row * CELL + CELL / 2, 23, Font.BOLD, INK);
        drawText(g, "Строки — слева, столбцы — сверху.", x, y + height + 28, 12, Font.PLAIN, MUTED);
    }

    /**
     * Рисует правый список слов. В режиме puzzle — просто слова с номерами
     * в нейтральных бейджах; в режиме solution — те же слова, но бейдж
     * окрашен в цвет подсветки, а под словом добавлены координаты.
     *
     * <p>Координаты в списке намеренно 1-based: читателю удобнее, а JSON
     * остаётся 0-based для программ.
     */
    private static void drawWordList(Graphics2D g, List<WordSearchGenerator.Placement> placements,
            boolean solution, int rowSpacing) {
        int x = RIGHT_X + 24;
        drawText(g, solution ? "Найденные слова" : "Слова для поиска", x, PANEL_TOP + 36, 20, Font.BOLD, INK);
        drawText(g, solution ? "Цвет показывает положение слова" : "Слова читаются в 8 направлениях",
            x, PANEL_TOP + 59, 12, Font.PLAIN, MUTED);
        if (solution) drawText(g, "Координаты: строка, столбец", x, PANEL_TOP + 78, 11, Font.PLAIN, MUTED);

        int firstY = PANEL_TOP + LIST_TOP_PADDING;
        for (int i = 0; i < placements.size(); i++) {
            WordSearchGenerator.Placement p = placements.get(i);
            int itemY = firstY + i * rowSpacing;
            Color color = solution ? highlightColor(i) : BADGE;
            // В решении бейдж полупрозрачный, чтобы не спорить с текстом;
            // в puzzle — сплошной нейтральный, чтобы не отвлекать.
            g.setColor(solution ? new Color(color.getRed(), color.getGreen(), color.getBlue(), 65) : color);
            g.fillRoundRect(x, itemY - 15, 28, 28, 10, 10);
            drawCentered(g, Integer.toString(i + 1), x + 14, itemY - 1, 12, Font.BOLD, INK);

            int wordX = x + 40;
            int maxWidth = RIGHT_WIDTH - 24 - 24 - 40;
            // Автоподбор шрифта: длинное слово уменьшаем до 12 px, но не мельче.
            // Альтернатива — перенос строки, но в узком списке он выглядит хуже,
            // чем чуть меньший шрифт.
            int wordSize = 17;
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, wordSize));
            while (wordSize > 12 && g.getFontMetrics().stringWidth(p.word()) > maxWidth) {
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, --wordSize));
            }
            drawText(g, p.word(), wordX, itemY + 3, wordSize, Font.BOLD, INK);
            if (solution) {
                int distance = p.word().length() - 1;
                String coordinates = "(" + (p.row() + 1) + ", " + (p.col() + 1) + ") → ("
                    + (p.row() + distance * p.dr() + 1) + ", " + (p.col() + distance * p.dc() + 1) + ")";
                drawText(g, coordinates, wordX, itemY + 21, 12, Font.PLAIN, MUTED);
            }
            // Разделитель между пунктами: визуально отделяет слова друг от друга,
            // не превращая список в таблицу.
            if (i + 1 < placements.size()) {
                g.setColor(DIVIDER);
                g.drawLine(wordX, itemY + rowSpacing - 20, RIGHT_X + RIGHT_WIDTH - 24, itemY + rowSpacing - 20);
            }
        }
    }

    /** Удобная обёртка: drawText с выравниванием по левому краю (baseline). */
    private static void drawText(Graphics2D g, String value, int x, int y,
            int size, int style, Color color) {
        drawText(g, value, x, y, size, style, color, false);
    }

    /** Удобная обёртка: drawText с центрированием по (centerX, centerY). */
    private static void drawCentered(Graphics2D g, String value, int centerX, int centerY,
            int size, int style, Color color) {
        drawText(g, value, centerX, centerY, size, style, color, true);
    }

    /**
     * Один общий метод для обоих режимов. Центрирование считается через
     * FontMetrics: по горизонтали — половина ширины строки, по вертикали —
     * половина разности ascent/descent. Это даёт визуальный центр, а не
     * «baseline по центру», который для кириллицы смотрится криво.
     */
    private static void drawText(Graphics2D g, String value, int x, int y,
            int size, int style, Color color, boolean center) {
        g.setFont(new Font(Font.SANS_SERIF, style, size));
        g.setColor(color);
        if (center) {
            FontMetrics metrics = g.getFontMetrics();
            float dx = x - metrics.stringWidth(value) / 2f;
            float dy = y + (metrics.getAscent() - metrics.getDescent()) / 2f;
            g.drawString(value, dx, dy);
        } else {
            g.drawString(value, x, y);
        }
    }

    /**
     * Цвет подсветки по индексу слова. Первые 12 берутся из палитры;
     * дальше — золотое сечение в HSB: соседние индексы дают далёкие оттенки,
     * поэтому даже 20-е слово не сольётся с 19-м.
     */
    private static Color highlightColor(int index) {
        if (index < PALETTE.length) return new Color(PALETTE[index]);
        float hue = (float) ((index * 0.6180339887498949) % 1.0);
        return Color.getHSBColor(hue, 0.47f, 0.72f);
    }
}
