import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Standalone contract tests: run with java -cp build/classes GeneratorTest. */
public final class GeneratorTest {
    private static final List<String> PDF_WORDS = List.of(
            "май", "жена", "шляпа", "пояс", "кухня", "ковер",
            "бровь", "директор", "слово", "март", "учитель");
    private static int checks;
    private static int scenarios;

    public static void main(String[] args) {
        int[][] sizes = {{10, 10}, {11, 10}, {10, 11}, {12, 10}, {10, 12}};
        for (int[] size : sizes) {
            checkPuzzle(size[0], size[1], PDF_WORDS, 2026L);
            pass("PDF dictionary on " + size[0] + "x" + size[1]);
        }

        String first = checkPuzzle(10, 10, PDF_WORDS, 42L);
        String second = checkPuzzle(10, 10, PDF_WORDS, 42L);
        check(first.equals(second), "The same seed must reproduce grid and placements");
        pass("Seed reproducibility");

        // Decomposed Ё, a palindrome, and a required subword occurrence.
        List<String> special = List.of(
                "е\u0308лка", "топот", "кот", "котик", "луна", "река",
                "зонт", "мышь", "лес", "море", "дом");
        checkPuzzle(10, 10, special, 73L);
        pass("NFC, Ё, palindrome, and shared subword");

        List<String> longWord = new ArrayList<>(PDF_WORDS);
        longWord.add("ЭЛЕКТРОПОЕЗД");
        check("ЭЛЕКТРОПОЕЗД".length() == 12, "Boundary fixture must have length 12");
        checkPuzzle(10, 12, longWord, 123L);
        var wide = WordSearchGenerator.generate(10, 12, longWord, 123L);
        var boundary = wide.placements().stream()
                .filter(p -> p.word().equals("ЭЛЕКТРОПОЕЗД"))
                .findFirst().orElseThrow(() -> new AssertionError("Missing boundary word"));
        check(boundary.dr() == 0 && Math.abs(boundary.dc()) == 1,
                "A 12-letter word on a 10x12 board must be horizontal");
        expectReject("12-letter word on 10x10", () ->
                WordSearchGenerator.generate(10, 10, longWord, 123L));
        pass("Word length boundary and direction");

        expectReject("fewer than ten words", () ->
                WordSearchGenerator.generate(10, 10, PDF_WORDS.subList(0, 9), 1L));

        List<String> duplicateCase = new ArrayList<>(PDF_WORDS);
        duplicateCase.add("МАЙ");
        expectReject("case-normalized duplicate", () ->
                WordSearchGenerator.generate(10, 10, duplicateCase, 1L));

        List<String> duplicateNfc = new ArrayList<>(PDF_WORDS);
        duplicateNfc.add("ёлка");
        duplicateNfc.add("Е\u0308ЛКА");
        expectReject("NFC-normalized duplicate", () ->
                WordSearchGenerator.generate(10, 10, duplicateNfc, 1L));

        expectReject("unsupported dimensions", () ->
                WordSearchGenerator.generate(11, 11, PDF_WORDS, 1L));
        expectReject("zero dimensions", () ->
                WordSearchGenerator.generate(0, 10, PDF_WORDS, 1L));

        List<String> latin = new ArrayList<>(PDF_WORDS);
        latin.set(0, "JAVA");
        expectReject("Latin letters", () ->
                WordSearchGenerator.generate(10, 10, latin, 1L));
        pass("Invalid inputs rejected");

        // Public validation must inspect the grid, not merely trust placement metadata.
        var original = WordSearchGenerator.generate(10, 10, PDF_WORDS, 99L);
        char[][] broken = Arrays.stream(original.grid()).map(char[]::clone).toArray(char[][]::new);
        var placement = original.placements().get(0);
        char old = broken[placement.row()][placement.col()];
        broken[placement.row()][placement.col()] = old == 'А' ? 'Б' : 'А';
        var damaged = new WordSearchGenerator.Puzzle(broken, original.placements(), original.seed());
        expectReject("grid inconsistent with metadata", () -> WordSearchGenerator.validate(damaged));
        pass("Public validator detects damaged grid");

        System.out.println("PASS: " + scenarios + " scenarios, " + checks + " assertions");
    }

    private static String checkPuzzle(int rows, int cols, List<String> rawWords, long seed) {
        var puzzle = WordSearchGenerator.generate(rows, cols, rawWords, seed);
        char[][] grid = puzzle.grid();
        check(grid.length == rows, "Wrong number of rows");
        check(puzzle.seed() == seed, "Seed was not retained");
        for (char[] row : grid) {
            check(row.length == cols, "Wrong number of columns");
            for (char letter : row) {
                check(isRussianUppercase(letter), "Empty or non-Russian grid cell: " + letter);
            }
        }

        Set<String> expected = new LinkedHashSet<>();
        for (String word : rawWords) expected.add(normalize(word));
        check(expected.size() >= 10, "Test fixture must have at least ten distinct words");
        check(puzzle.placements().size() == expected.size(), "Not all input words were placed");

        Set<String> actual = new HashSet<>();
        Set<String> orientations = new HashSet<>();
        for (var p : puzzle.placements()) {
            check(expected.contains(p.word()), "Unexpected placement word: " + p.word());
            check(actual.add(p.word()), "Duplicate placement metadata: " + p.word());
            check(p.word().equals(normalize(p.word())), "Word was not normalized");
            check(Math.abs(p.dr()) <= 1 && Math.abs(p.dc()) <= 1
                            && (p.dr() != 0 || p.dc() != 0),
                    "Invalid straight-line direction");

            for (int i = 0; i < p.word().length(); i++) {
                int r = p.row() + i * p.dr();
                int c = p.col() + i * p.dc();
                check(r >= 0 && r < rows && c >= 0 && c < cols,
                        "Word extends beyond grid: " + p.word());
                check(grid[r][c] == p.word().charAt(i),
                        "Grid does not match placement: " + p.word());
            }
            orientations.add(p.dr() == 0 ? "H" : p.dc() == 0 ? "V" : "D");
        }
        check(actual.equals(expected), "Placed word set differs from normalized input");
        check(orientations.containsAll(Set.of("H", "V", "D")),
                "Expected horizontal, vertical and diagonal placements");
        for (String word : expected) {
            int count = independentOccurrences(grid, word);
            check(count == 1, "Expected exactly one occurrence of " + word + ", got " + count);
        }
        WordSearchGenerator.validate(puzzle);
        return Arrays.deepToString(grid) + "|" + puzzle.placements() + "|" + puzzle.seed();
    }

    /** Independent exhaustive scan of the completed grid in all eight directions. */
    private static int independentOccurrences(char[][] grid, String word) {
        Set<String> paths = new HashSet<>();
        int rows = grid.length;
        int cols = grid[0].length;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        if (dr == 0 && dc == 0) continue;
                        int lastR = r + (word.length() - 1) * dr;
                        int lastC = c + (word.length() - 1) * dc;
                        if (lastR < 0 || lastR >= rows || lastC < 0 || lastC >= cols) continue;
                        boolean matches = true;
                        for (int i = 0; i < word.length(); i++) {
                            if (grid[r + i * dr][c + i * dc] != word.charAt(i)) {
                                matches = false;
                                break;
                            }
                        }
                        if (matches) {
                            String forward = pathKey(r, c, dr, dc, word.length());
                            String reverse = pathKey(lastR, lastC, -dr, -dc, word.length());
                            paths.add(forward.compareTo(reverse) <= 0 ? forward : reverse);
                        }
                    }
                }
            }
        }
        return paths.size();
    }

    private static String pathKey(int r, int c, int dr, int dc, int length) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length; i++) {
            result.append(r + i * dr).append(',').append(c + i * dc).append(';');
        }
        return result.toString();
    }

    private static String normalize(String word) {
        return Normalizer.normalize(word.trim(), Normalizer.Form.NFC).toUpperCase(Locale.ROOT);
    }

    private static boolean isRussianUppercase(char letter) {
        return (letter >= 'А' && letter <= 'Я') || letter == 'Ё';
    }

    private static void expectReject(String label, Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            checks++;
            return;
        }
        throw new AssertionError("Expected input/validation rejection: " + label);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static void pass(String label) {
        scenarios++;
        System.out.println("OK: " + label);
    }
}
