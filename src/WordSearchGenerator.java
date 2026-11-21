import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/** Generates and independently checks Russian word-search puzzles. */
public final class WordSearchGenerator {
    public static final String ALPHABET = "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ";

    /** Coordinates are zero-based; dr/dc describe one of eight directions. */
    public record Placement(String word, int row, int col, int dr, int dc) {}

    /** placements contains one actual occurrence for every normalized input word. */
    public record Puzzle(char[][] grid, List<Placement> placements, long seed) {}

    private static final int[][] DIRECTIONS = {
        {0, 1}, {0, -1}, {1, 0}, {-1, 0},
        {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };
    private static final int HORIZONTAL = 1;
    private static final int VERTICAL = 2;
    private static final int DIAGONAL = 4;
    private static final int ALL_DIRECTIONS = HORIZONTAL | VERTICAL | DIAGONAL;
    private static final long ALL_LETTERS = (1L << ALPHABET.length()) - 1;
    private static final int MAX_ATTEMPTS = 16;
    private static final int MAX_SEARCH_STEPS = 200_000;
    private static final int PLACEMENT_STEPS_PER_ATTEMPT = 12_000;
    private static final int FILL_STEPS_PER_ATTEMPT = 30_000;

    private WordSearchGenerator() {}

    /**
     * Places every supplied word, then fills the remaining cells randomly.
     * The same dimensions, ordered word list, and seed reproduce the same result.
     * A returned puzzle always passes validate; exhausting the deterministic
     * search budget throws IllegalStateException rather than returning a bad grid.
     */
    public static Puzzle generate(int rows, int cols, List<String> rawWords, long seed) {
        requireSupportedSize(rows, cols);
        if (rawWords == null) {
            throw new IllegalArgumentException("Не задан список слов.");
        }
        List<String> words = new ArrayList<>();
        Set<String> distinct = new LinkedHashSet<>();
        for (String rawWord : rawWords) {
            String word = normalizeWord(rawWord);
            if (word.length() > Math.max(rows, cols)) {
                throw new IllegalArgumentException(
                    "Слово «" + word + "» слишком длинное для поля " + rows + "×" + cols + ".");
            }
            if (!distinct.add(word)) {
                throw new IllegalArgumentException(
                    "Повтор слова после нормализации: «" + word + "».");
            }
            words.add(word);
        }
        if (words.size() < 10) {
            throw new IllegalArgumentException("Нужно минимум 10 различных слов.");
        }

        Budget budget = new Budget(MAX_SEARCH_STEPS);
        for (int attempt = 0; attempt < MAX_ATTEMPTS && budget.remaining > 0; attempt++) {
            long attemptSeed = mix64(seed + 0x9E3779B97F4A7C15L * attempt);
            Search search = new Search(rows, cols, words, new Random(attemptSeed), budget);
            if (search.placeWords()) {
                Puzzle puzzle = search.result(seed);
                validate(puzzle);
                return puzzle;
            }
        }
        throw new IllegalStateException(
            "Не удалось сгенерировать поле за лимит поиска. "
            + "Попробуйте другой seed, размер поля или набор слов.");
    }

    /**
     * Checks the finished grid and its metadata independently of generation.
     * Throws IllegalArgumentException if any requirement is violated.
     */
    public static void validate(Puzzle puzzle) {
        if (puzzle == null) {
            throw new IllegalArgumentException("Поле не задано.");
        }
        char[][] grid = puzzle.grid();
        int cols = requireRectangularGrid(grid);
        int rows = grid.length;
        requireSupportedSize(rows, cols);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (ALPHABET.indexOf(grid[row][col]) < 0) {
                    throw new IllegalArgumentException(
                        "В клетке (" + row + ", " + col + ") нет заглавной русской буквы.");
                }
            }
        }
        List<Placement> placements = puzzle.placements();
        if (placements == null || placements.size() < 10) {
            throw new IllegalArgumentException("В решении должно быть минимум 10 различных слов.");
        }
        Set<String> words = new HashSet<>();
        int directions = 0;
        for (Placement placement : placements) {
            if (placement == null) {
                throw new IllegalArgumentException("Решение содержит пустое размещение.");
            }
            String word = normalizeWord(placement.word());
            if (!word.equals(placement.word())) {
                throw new IllegalArgumentException("Слово в решении не нормализовано: «" + word + "».");
            }
            if (!words.add(word)) {
                throw new IllegalArgumentException("Слово повторяется в решении: «" + word + "».");
            }
            if (word.length() > Math.max(rows, cols)) {
                throw new IllegalArgumentException("Слишком длинное слово в решении: «" + word + "».");
            }
            int dr = placement.dr();
            int dc = placement.dc();
            if (dr < -1 || dr > 1 || dc < -1 || dc > 1 || (dr == 0 && dc == 0)) {
                throw new IllegalArgumentException("Неверное направление слова «" + word + "».");
            }
            int endRow = placement.row() + (word.length() - 1) * dr;
            int endCol = placement.col() + (word.length() - 1) * dc;
            if (!inside(placement.row(), placement.col(), rows, cols)
                    || !inside(endRow, endCol, rows, cols)) {
                throw new IllegalArgumentException("Слово «" + word + "» выходит за границы поля.");
            }
            for (int i = 0; i < word.length(); i++) {
                if (grid[placement.row() + i * dr][placement.col() + i * dc] != word.charAt(i)) {
                    throw new IllegalArgumentException(
                        "Буквы поля не совпадают с размещением слова «" + word + "».");
                }
            }
            List<Placement> occurrences = findOccurrences(grid, word);
            if (occurrences.size() != 1) {
                throw new IllegalArgumentException(
                    "Слово «" + word + "» встречается " + occurrences.size()
                    + " раз; требуется ровно одно физическое вхождение.");
            }
            if (!segmentKey(placement, cols).equals(segmentKey(occurrences.get(0), cols))) {
                throw new IllegalArgumentException("Координаты слова «" + word + "» неверны.");
            }
            directions |= directionKind(dr, dc);
        }
        if (directions != ALL_DIRECTIONS) {
            throw new IllegalArgumentException(
                "Нужны горизонтальное, вертикальное и диагональное размещения.");
        }
    }

    /**
     * Searches all eight directions in any nonempty rectangular grid.
     * An unknown/empty cell cannot match a letter. A palindrome read in both
     * directions on the same segment is one physical occurrence.
     */
    public static List<Placement> findOccurrences(char[][] grid, String rawWord) {
        int cols = requireRectangularGrid(grid);
        int rows = grid.length;
        String word = normalizeWord(rawWord);
        if (word.length() > Math.max(rows, cols)) {
            return List.of();
        }
        List<Placement> found = new ArrayList<>();
        Set<SegmentKey> seen = new HashSet<>();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                for (int[] direction : DIRECTIONS) {
                    int dr = direction[0];
                    int dc = direction[1];
                    int endRow = row + (word.length() - 1) * dr;
                    int endCol = col + (word.length() - 1) * dc;
                    if (!inside(endRow, endCol, rows, cols)) {
                        continue;
                    }
                    boolean matches = true;
                    for (int i = 0; i < word.length(); i++) {
                        if (grid[row + i * dr][col + i * dc] != word.charAt(i)) {
                            matches = false;
                            break;
                        }
                    }
                    if (matches) {
                        Placement placement = new Placement(word, row, col, dr, dc);
                        if (seen.add(segmentKey(placement, cols))) {
                            found.add(placement);
                        }
                    }
                }
            }
        }
        return List.copyOf(found);
    }

    private static String normalizeWord(String rawWord) {
        if (rawWord == null) {
            throw new IllegalArgumentException("Список содержит пустое слово (null).");
        }
        String word = Normalizer.normalize(rawWord.trim(), Normalizer.Form.NFC)
            .toUpperCase(Locale.ROOT);
        if (word.length() < 2) {
            throw new IllegalArgumentException("Каждое слово должно содержать минимум две буквы.");
        }
        for (int i = 0; i < word.length(); i++) {
            if (ALPHABET.indexOf(word.charAt(i)) < 0) {
                throw new IllegalArgumentException(
                    "Слово «" + word + "» должно содержать только русские буквы А–Я и Ё.");
            }
        }
        return word;
    }

    private static void requireSupportedSize(int rows, int cols) {
        if (!((rows == 10 && (cols == 10 || cols == 11 || cols == 12))
                || (cols == 10 && (rows == 11 || rows == 12)))) {
            throw new IllegalArgumentException(
                "Допустимые размеры (строки × столбцы): 10×10, 11×10, 10×11, 12×10, 10×12.");
        }
    }

    private static int requireRectangularGrid(char[][] grid) {
        if (grid == null || grid.length == 0 || grid[0] == null || grid[0].length == 0) {
            throw new IllegalArgumentException("Поле должно быть непустым прямоугольником.");
        }
        int cols = grid[0].length;
        for (char[] row : grid) {
            if (row == null || row.length != cols) {
                throw new IllegalArgumentException("Все строки поля должны иметь одинаковую длину.");
            }
        }
        return cols;
    }

    private static boolean inside(int row, int col, int rows, int cols) {
        return row >= 0 && row < rows && col >= 0 && col < cols;
    }

    private static int directionKind(int dr, int dc) {
        return dr == 0 ? HORIZONTAL : dc == 0 ? VERTICAL : DIAGONAL;
    }

    private record SegmentKey(long first, long last) {}

    private static SegmentKey segmentKey(Placement placement, int cols) {
        int distance = placement.word().length() - 1;
        long first = (long) placement.row() * cols + placement.col();
        long last = (long) (placement.row() + distance * placement.dr()) * cols
            + placement.col() + distance * placement.dc();
        return new SegmentKey(Math.min(first, last), Math.max(first, last));
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static final class Budget {
        private int remaining;

        Budget(int remaining) {
            this.remaining = remaining;
        }

        boolean take() {
            if (remaining <= 0) {
                return false;
            }
            remaining--;
            return true;
        }
    }

    /** One possible oriented occurrence; reversed palindromes are deduplicated. */
    private static final class Window {
        final int wordIndex;
        final String word;
        final Placement placement;
        final int[] cells;
        int empty;
        int mismatches;

        Window(int wordIndex, String word, Placement placement, int[] cells) {
            this.wordIndex = wordIndex;
            this.word = word;
            this.placement = placement;
            this.cells = cells;
            this.empty = cells.length;
        }

        boolean complete() {
            return empty == 0 && mismatches == 0;
        }
    }

    private record Link(Window window, int position) {}

    private static final class Search {
        private final int rows;
        private final int cols;
        private final List<String> words;
        private final Random random;
        private final Budget budget;
        private final char[] grid;
        private final List<Window> windows = new ArrayList<>();
        private final List<List<Window>> byWord = new ArrayList<>();
        private final List<List<Link>> byCell = new ArrayList<>();
        private final int[] occurrenceCounts;
        private final List<Integer> fillOrder = new ArrayList<>();
        private int duplicatedWords;
        private int placementSteps = PLACEMENT_STEPS_PER_ATTEMPT;
        private int fillSteps = FILL_STEPS_PER_ATTEMPT;
        private boolean exhausted;

        Search(int rows, int cols, List<String> words, Random random, Budget budget) {
            this.rows = rows;
            this.cols = cols;
            this.words = words;
            this.random = random;
            this.budget = budget;
            this.grid = new char[rows * cols];
            this.occurrenceCounts = new int[words.size()];
            for (int cell = 0; cell < grid.length; cell++) {
                byCell.add(new ArrayList<>());
                fillOrder.add(cell);
            }
            Collections.shuffle(fillOrder, random);
            for (int wordIndex = 0; wordIndex < words.size(); wordIndex++) {
                String word = words.get(wordIndex);
                List<Window> wordWindows = new ArrayList<>();
                byWord.add(wordWindows);
                boolean palindrome = word.equals(new StringBuilder(word).reverse().toString());
                for (int row = 0; row < rows; row++) {
                    for (int col = 0; col < cols; col++) {
                        for (int[] direction : DIRECTIONS) {
                            int dr = direction[0];
                            int dc = direction[1];
                            int endRow = row + (word.length() - 1) * dr;
                            int endCol = col + (word.length() - 1) * dc;
                            if (!inside(endRow, endCol, rows, cols)) {
                                continue;
                            }
                            if (palindrome && row * cols + col > endRow * cols + endCol) {
                                continue;
                            }
                            int[] cells = new int[word.length()];
                            for (int i = 0; i < cells.length; i++) {
                                cells[i] = (row + i * dr) * cols + col + i * dc;
                            }
                            Window window = new Window(wordIndex, word,
                                new Placement(word, row, col, dr, dc), cells);
                            windows.add(window);
                            wordWindows.add(window);
                            for (int i = 0; i < cells.length; i++) {
                                byCell.get(cells[i]).add(new Link(window, i));
                            }
                        }
                    }
                }
            }
        }

        boolean placeWords() {
            if (exhausted || duplicatedWords != 0) {
                return false;
            }
            int chosenWord = -1;
            int unmatched = 0;
            int coverage = 0;
            for (int i = 0; i < words.size(); i++) {
                if (occurrenceCounts[i] == 1) {
                    Placement found = singleOccurrence(i).placement;
                    coverage |= directionKind(found.dr(), found.dc());
                } else {
                    unmatched++;
                    if (chosenWord < 0 || words.get(i).length() > words.get(chosenWord).length()) {
                        chosenWord = i;
                    }
                }
            }
            if (Integer.bitCount(ALL_DIRECTIONS & ~coverage) > unmatched) {
                return false;
            }
            if (chosenWord < 0) {
                return coverage == ALL_DIRECTIONS && fillEmptyCells();
            }

            List<Window> candidates = new ArrayList<>();
            for (Window window : byWord.get(chosenWord)) {
                if (window.mismatches == 0) {
                    candidates.add(window);
                }
            }
            Collections.shuffle(candidates, random);
            final int currentCoverage = coverage;
            candidates.sort(Comparator
                .comparingInt((Window window) ->
                    (directionKind(window.placement.dr(), window.placement.dc()) & currentCoverage) == 0
                    ? 0 : 1)
                .thenComparingInt(window -> window.empty));

            for (Window candidate : candidates) {
                if (!takePlacementStep()) {
                    return false;
                }
                int[] changed = new int[candidate.cells.length];
                int changedCount = 0;
                for (int i = 0; i < candidate.cells.length; i++) {
                    int cell = candidate.cells[i];
                    if (grid[cell] == 0) {
                        changed[changedCount++] = cell;
                        setCell(cell, candidate.word.charAt(i));
                        if (duplicatedWords != 0) {
                            break;
                        }
                    }
                }
                if (duplicatedWords == 0 && placeWords()) {
                    return true;
                }
                for (int i = changedCount - 1; i >= 0; i--) {
                    clearCell(changed[i]);
                }
                if (exhausted) {
                    return false;
                }
            }
            return false;
        }

        /** MRV plus one-empty-cell constraints prevents accidental duplicates. */
        private boolean fillEmptyCells() {
            if (exhausted || duplicatedWords != 0) {
                return false;
            }
            long[] forbidden = new long[grid.length];
            for (Window window : windows) {
                if (window.mismatches == 0 && window.empty == 1) {
                    for (int i = 0; i < window.cells.length; i++) {
                        int cell = window.cells[i];
                        if (grid[cell] == 0) {
                            int letter = ALPHABET.indexOf(window.word.charAt(i));
                            forbidden[cell] |= 1L << letter;
                            break;
                        }
                    }
                }
            }
            int chosenCell = -1;
            int smallestDomain = ALPHABET.length() + 1;
            for (int cell : fillOrder) {
                if (grid[cell] != 0) {
                    continue;
                }
                int domainSize = ALPHABET.length() - Long.bitCount(forbidden[cell]);
                if (domainSize == 0) {
                    return false;
                }
                if (domainSize < smallestDomain) {
                    smallestDomain = domainSize;
                    chosenCell = cell;
                }
            }
            if (chosenCell < 0) {
                return true;
            }
            long allowed = ALL_LETTERS & ~forbidden[chosenCell];
            List<Character> letters = new ArrayList<>();
            for (int i = 0; i < ALPHABET.length(); i++) {
                if ((allowed & (1L << i)) != 0) {
                    letters.add(ALPHABET.charAt(i));
                }
            }
            Collections.shuffle(letters, random);
            for (char letter : letters) {
                if (!takeFillStep()) {
                    return false;
                }
                setCell(chosenCell, letter);
                if (duplicatedWords == 0 && fillEmptyCells()) {
                    return true;
                }
                clearCell(chosenCell);
                if (exhausted) {
                    return false;
                }
            }
            return false;
        }

        /** Incrementally accounts for every word window touching this cell. */
        private void setCell(int cell, char value) {
            grid[cell] = value;
            for (Link link : byCell.get(cell)) {
                Window window = link.window();
                window.empty--;
                if (value != window.word.charAt(link.position())) {
                    window.mismatches++;
                }
                if (window.complete() && ++occurrenceCounts[window.wordIndex] == 2) {
                    duplicatedWords++;
                }
            }
        }

        private void clearCell(int cell) {
            char value = grid[cell];
            for (Link link : byCell.get(cell)) {
                Window window = link.window();
                if (window.complete() && occurrenceCounts[window.wordIndex]-- == 2) {
                    duplicatedWords--;
                }
                if (value != window.word.charAt(link.position())) {
                    window.mismatches--;
                }
                window.empty++;
            }
            grid[cell] = 0;
        }

        private Window singleOccurrence(int wordIndex) {
            for (Window window : byWord.get(wordIndex)) {
                if (window.complete()) {
                    return window;
                }
            }
            throw new IllegalStateException("Внутренняя ошибка учёта вхождений.");
        }

        private boolean takePlacementStep() {
            if (placementSteps-- <= 0 || !budget.take()) {
                exhausted = true;
                return false;
            }
            return true;
        }

        private boolean takeFillStep() {
            if (fillSteps-- <= 0 || !budget.take()) {
                exhausted = true;
                return false;
            }
            return true;
        }

        Puzzle result(long seed) {
            char[][] resultGrid = new char[rows][cols];
            for (int row = 0; row < rows; row++) {
                System.arraycopy(grid, row * cols, resultGrid[row], 0, cols);
            }
            List<Placement> placements = new ArrayList<>();
            for (int i = 0; i < words.size(); i++) {
                placements.add(singleOccurrence(i).placement);
            }
            return new Puzzle(resultGrid, List.copyOf(placements), seed);
        }
    }
}
