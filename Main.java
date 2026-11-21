/*
 * Вариант 5 — Words. Полная программа в одном файле.
 * Требуется JDK 17 или новее. Дополнительные библиотеки и файлы не нужны.
 * Запуск: java "-Dfile.encoding=UTF-8" Main.java
 * Другой размер: java "-Dfile.encoding=UTF-8" Main.java --size 10x12 --seed 123
 * Результаты сохраняются в папку output внутри текущей рабочей папки.
 * Ввод своего списка: добавьте --words words.txt (UTF-8, одно слово на строку).
 */

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.text.Normalizer;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Command-line entry point and UTF-8 exports for assignment 5, Words. */
public final class Main {
    private record Options(int rows, int cols, long seed, Path words, Path output) {}

    public static void main(String[] args) {
        try {
            for (String arg : args) {
                if (arg.equals("--help") || arg.equals("-h")) {
                    help();
                    return;
                }
            }
            Options options = parseOptions(args);
            List<String> words = readWords(options.words());
            WordSearchGenerator.Puzzle puzzle = WordSearchGenerator.generate(
                    options.rows(), options.cols(), words, options.seed());
            WordSearchGenerator.validate(puzzle);

            Files.createDirectories(options.output());
            String text = toText(puzzle);
            String json = toJson(puzzle);
            Files.writeString(options.output().resolve("puzzle.txt"), text, StandardCharsets.UTF_8);
            Files.writeString(options.output().resolve("puzzle.json"), json, StandardCharsets.UTF_8);
            Files.writeString(options.output().resolve("puzzle.html"), toHtml(puzzle, json), StandardCharsets.UTF_8);
            PuzzleRenderer.writeImages(puzzle, options.output());
            System.out.print(text);
            System.out.println("Файлы сохранены в: " + options.output().toAbsolutePath().normalize());
            System.out.println("Откройте puzzle.html, чтобы показать или скрыть решение.");
        } catch (IllegalArgumentException | IllegalStateException | IOException exception) {
            System.err.println("Ошибка: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static Options parseOptions(String[] args) {
        int rows = 10;
        int cols = 10;
        long seed = 42;
        Path words = null; // No external input file is required by default.
        Path output = Path.of("output");
        for (int i = 0; i < args.length; i++) {
            String flag = args[i];
            if (!List.of("--size", "--seed", "--words", "--out").contains(flag)) {
                throw new IllegalArgumentException("Неизвестный параметр: " + flag + ". Справка: --help");
            }
            if (++i >= args.length) throw new IllegalArgumentException("Нет значения после " + flag);
            String value = args[i];
            switch (flag) {
                case "--size" -> {
                    String[] parts = value.toLowerCase(Locale.ROOT).replace('х', 'x').replace('×', 'x').split("x", -1);
                    if (parts.length != 2) throw new IllegalArgumentException("Размер задаётся как строки x столбцы, например 10x12.");
                    try {
                        rows = Integer.parseInt(parts[0]);
                        cols = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException exception) {
                        throw new IllegalArgumentException("Размер должен содержать два целых числа, например 10x12.");
                    }
                }
                case "--seed" -> {
                    try {
                        seed = Long.parseLong(value);
                    } catch (NumberFormatException exception) {
                        throw new IllegalArgumentException("Seed должен быть целым числом типа long.");
                    }
                }
                case "--words" -> words = Path.of(value);
                case "--out" -> output = Path.of(value);
                default -> throw new AssertionError("Unreachable option");
            }
        }
        return new Options(rows, cols, seed, words, output);
    }

    private static List<String> readWords(Path path) throws IOException {
        if (path == null) {
            return List.of("май", "жена", "шляпа", "пояс", "кухня", "ковер",
                    "бровь", "директор", "слово", "март", "учитель");
        }
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<String> words = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (i == 0 && line.startsWith("\uFEFF")) line = line.substring(1);
            line = line.strip();
            if (!line.isEmpty() && !line.startsWith("#")) words.add(line);
        }
        return words;
    }

    private static void help() {
        System.out.println("""
                Words — вариант 5. Генератор поиска слов в буквенной сетке.

                java Main.java [параметры]
                  --size 10x10       Строки x столбцы. Также 11x10, 10x11, 12x10, 10x12.
                  --seed 42          Начальное значение генератора случайных чисел.
                  --words words.txt  Необязательный UTF-8 файл, одно русское слово на строку.
                  --out output       Папка для TXT, JSON, HTML и двух PNG-изображений.
                  --help             Эта справка.

                Без --words используются 11 встроенных слов из задания.
                По умолчанию: 10x10, seed 42, папка output.
                Координаты в консоли с 1, в JSON с 0.
                """);
    }

    private static String toText(WordSearchGenerator.Puzzle puzzle) {
        char[][] grid = puzzle.grid();
        StringBuilder out = new StringBuilder();
        out.append("WORDS — ВАРИАНТ 5\n")
                .append("Размер: ").append(grid.length).append(" x ").append(grid[0].length)
                .append(" (строки x столбцы)\n")
                .append("Seed: ").append(puzzle.seed()).append('\n')
                .append("Размещено слов: ").append(puzzle.placements().size()).append("\n\n");
        for (char[] row : grid) {
            for (int col = 0; col < row.length; col++) {
                if (col > 0) out.append(' ');
                out.append(row[col]);
            }
            out.append('\n');
        }
        out.append("\nРЕШЕНИЕ (строка, столбец; нумерация с 1)\n");
        for (WordSearchGenerator.Placement word : puzzle.placements()) {
            int last = word.word().length() - 1;
            out.append(String.format(Locale.ROOT, "%s: (%d, %d) -> (%d, %d), %s%n",
                    word.word(), word.row() + 1, word.col() + 1,
                    word.row() + word.dr() * last + 1, word.col() + word.dc() * last + 1,
                    direction(word.dr(), word.dc())));
        }
        out.append("\nПроверка: каждое слово встречается ровно один раз.\n");
        return out.toString();
    }

    private static String direction(int dr, int dc) {
        if (dr == 0) return dc > 0 ? "вправо" : "влево";
        if (dc == 0) return dr > 0 ? "вниз" : "вверх";
        return (dr > 0 ? "вниз" : "вверх") + (dc > 0 ? "-вправо" : "-влево");
    }

    private static String toJson(WordSearchGenerator.Puzzle puzzle) {
        char[][] grid = puzzle.grid();
        StringBuilder out = new StringBuilder();
        out.append("{\n  \"rows\": ").append(grid.length)
                .append(",\n  \"columns\": ").append(grid[0].length)
                .append(",\n  \"seed\": ").append(puzzle.seed())
                .append(",\n  \"coordinateBase\": 0,\n  \"grid\": [\n");
        for (int row = 0; row < grid.length; row++) {
            out.append("    ").append(quote(new String(grid[row])));
            out.append(row + 1 < grid.length ? ",\n" : "\n");
        }
        out.append("  ],\n  \"words\": [\n");
        for (int i = 0; i < puzzle.placements().size(); i++) {
            WordSearchGenerator.Placement word = puzzle.placements().get(i);
            int last = word.word().length() - 1;
            out.append("    {\"text\": ").append(quote(word.word()))
                    .append(", \"row\": ").append(word.row())
                    .append(", \"col\": ").append(word.col())
                    .append(", \"dr\": ").append(word.dr())
                    .append(", \"dc\": ").append(word.dc())
                    .append(", \"endRow\": ").append(word.row() + word.dr() * last)
                    .append(", \"endCol\": ").append(word.col() + word.dc() * last).append('}');
            out.append(i + 1 < puzzle.placements().size() ? ",\n" : "\n");
        }
        return out.append("  ]\n}\n").toString();
    }

    private static String quote(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 32) escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else escaped.append(c);
                }
            }
        }
        return escaped.append('"').toString();
    }

    private static String toHtml(WordSearchGenerator.Puzzle puzzle, String json) {
        String template = """
                <!doctype html>
                <html lang="ru">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <title>Words · Вариант 5</title>
                  <style>
                    :root { color-scheme:light; --ink:#233e37; --muted:#66756c; --paper:#faf8f1; --line:#dce2d9; }
                    * { box-sizing:border-box; }
                    body { margin:0; color:var(--ink); background:#eef1e8; font-family:Arial,Helvetica,sans-serif; }
                    main { max-width:1120px; margin:32px auto; padding:0 24px; }
                    .eyebrow { display:flex; justify-content:space-between; align-items:center; letter-spacing:2.5px; font-size:11px; font-weight:700; }
                    .stamp { background:#dce6d3; padding:9px 12px; border-radius:24px; letter-spacing:1px; }
                    header { border-bottom:1px solid #cdd7c8; padding-bottom:24px; margin-bottom:24px; }
                    h1 { font-size:48px; font-weight:500; letter-spacing:-2px; margin:16px 0 10px; }
                    .intro { margin:0; font-size:15px; color:var(--muted); line-height:1.5; }
                    .layout { display:grid; grid-template-columns:minmax(0,1fr) 270px; gap:24px; align-items:start; }
                    .sheet { background:var(--paper); border:1px solid var(--line); padding:24px; border-radius:12px; }
                    .sheet-top { display:flex; justify-content:space-between; margin-bottom:20px; font-size:12px; color:var(--muted); }
                    .board { position:relative; background:#fffef9; max-width:600px; margin:auto; border:1px solid #b8c7b9; }
                    .cells { position:relative; display:grid; z-index:2; }
                    .cell { display:flex; justify-content:center; align-items:center; aspect-ratio:1; border-right:1px solid #dce2d980; border-bottom:1px solid #dce2d980; font-size:clamp(15px,2.1vw,25px); font-weight:500; }
                    #lines { position:absolute; inset:0; width:100%; height:100%; z-index:1; pointer-events:none; }
                    #lines line { opacity:0; transition:opacity .18s; }
                    #lines line.visible { opacity:.47; }
                    .board-caption { font-size:12px; color:var(--muted); margin:18px 0 0; line-height:1.6; }
                    h2 { font-size:20px; font-weight:500; margin:0 0 8px; }
                    .aside-copy { font-size:13px; color:var(--muted); line-height:1.5; margin:0 0 18px; }
                    .words { display:flex; flex-wrap:wrap; gap:8px; margin-bottom:24px; }
                    .word { font:inherit; font-size:12px; font-weight:700; letter-spacing:.5px; background:#f9faf5; color:var(--ink); border:1px solid #cbd5c7; border-radius:6px; padding:9px 10px; cursor:pointer; }
                    .word.selected { background:#dce8d3; border-color:#7f9c7c; }
                    .actions { display:grid; gap:9px; }
                    .action { font:inherit; font-size:13px; font-weight:600; border-radius:6px; cursor:pointer; padding:13px 10px; border:1px solid var(--ink); background:var(--ink); color:#fff; }
                    .action.secondary { background:transparent; color:var(--ink); border-color:#becbbb; }
                    button:focus-visible { outline:3px solid #bc8e35; outline-offset:3px; }
                    .note { border-top:1px solid #cdd7c8; margin-top:24px; padding-top:18px; color:var(--muted); font-size:12px; line-height:1.7; }
                    .status { min-height:36px; font-size:12px; line-height:1.5; color:var(--ink); margin:16px 0 0; }
                    footer { margin-top:20px; display:flex; justify-content:space-between; color:var(--muted); font-size:11px; }
                    @media(max-width:740px) { main{margin:20px auto;padding:0 16px} h1{font-size:38px} .layout{grid-template-columns:1fr} .sheet{padding:15px} .cell{font-size:clamp(14px,4.1vw,24px)} }
                    @media print { body{background:white} main{margin:0;padding:0} .actions,.aside-copy{display:none} .layout{grid-template-columns:minmax(0,1fr) 190px;gap:18px} .word{padding:6px;font-size:10px} .sheet{break-inside:avoid} }
                  </style>
                </head>
                <body>
                <main>
                  <header>
                    <div class="eyebrow"><span>WORDS / ВАРИАНТ 05</span><span class="stamp">{{ROWS}} × {{COLS}}</span></div>
                    <h1>Найди слова</h1>
                    <p class="intro">{{COUNT}} слов спрятаны по горизонтали, вертикали и диагонали. Читать можно в обе стороны.</p>
                  </header>
                  <div class="layout">
                    <section class="sheet" aria-label="Буквенная сетка">
                      <div class="sheet-top"><span>БУКВЕННАЯ СЕТКА</span><span>Seed {{SEED}}</span></div>
                      <div class="board" id="board"><svg id="lines" aria-hidden="true"></svg><div class="cells" id="cells"></div></div>
                      <p class="board-caption">Каждое слово встречается ровно один раз. Слова идут по прямой и могут пересекаться.</p>
                    </section>
                    <aside>
                      <h2>Список слов</h2>
                      <p class="aside-copy">Нажмите на слово, чтобы увидеть его расположение.</p>
                      <div class="words" id="words"></div>
                      <div class="actions"><button class="action" id="toggle" aria-pressed="false">Показать решение</button><button class="action secondary" id="print">Распечатать</button></div>
                      <p class="status" id="status" aria-live="polite"></p>
                      <div class="note">Координаты подсказок: строка, столбец.<br>Нумерация начинается с 1.<br>«Ё» и «Е» — разные буквы.</div>
                    </aside>
                  </div>
                  <footer><span>JAVA · ГЕНЕРАЦИЯ И ПРОВЕРКА</span><span>Вариант 5 · Words</span></footer>
                </main>
                <script type="application/json" id="puzzle-data">{{JSON}}</script>
                <script>
                  const puzzle=JSON.parse(document.getElementById('puzzle-data').textContent);
                  const cells=document.getElementById('cells'), svg=document.getElementById('lines');
                  cells.style.gridTemplateColumns=`repeat(${puzzle.columns},1fr)`;
                  cells.setAttribute('role','table');
                  cells.setAttribute('aria-label',`Сетка ${puzzle.rows} на ${puzzle.columns}`);
                  puzzle.grid.forEach((row,r)=>Array.from(row).forEach((letter,c)=>{
                    const cell=document.createElement('div');cell.className='cell';cell.textContent=letter;
                    cell.setAttribute('aria-label',`Строка ${r+1}, столбец ${c+1}: ${letter}`);cells.append(cell);
                  }));
                  svg.setAttribute('viewBox',`0 0 ${puzzle.columns*48} ${puzzle.rows*48}`);
                  const palette=['#a6ba65','#d7a85c','#8bb7b7','#bd97c3','#9bc491','#d39591','#8eabd1','#d0bd7d'];
                  const wordButtons=[], lines=[];let showAll=false,selected=-1;
                  const status=document.getElementById('status'),toggle=document.getElementById('toggle');
                  function update(){
                    lines.forEach((line,i)=>line.classList.toggle('visible',showAll||selected===i));
                    wordButtons.forEach((button,i)=>{button.classList.toggle('selected',showAll||selected===i);button.setAttribute('aria-pressed',String(showAll||selected===i));});
                    toggle.textContent=showAll?'Скрыть решение':'Показать решение';toggle.setAttribute('aria-pressed',String(showAll));
                    if(showAll)status.textContent=`Показаны все ${puzzle.words.length} слов.`;
                    else if(selected>=0){const w=puzzle.words[selected];status.textContent=`${w.text}: (${w.row+1}, ${w.col+1}) → (${w.endRow+1}, ${w.endCol+1})`;}
                    else status.textContent='';
                  }
                  puzzle.words.forEach((w,i)=>{
                    const line=document.createElementNS('http://www.w3.org/2000/svg','line');
                    line.setAttribute('x1',(w.col+.5)*48);line.setAttribute('y1',(w.row+.5)*48);
                    line.setAttribute('x2',(w.endCol+.5)*48);line.setAttribute('y2',(w.endRow+.5)*48);
                    line.setAttribute('stroke',palette[i%palette.length]);line.setAttribute('stroke-width','30');line.setAttribute('stroke-linecap','round');svg.append(line);lines.push(line);
                    const button=document.createElement('button');button.className='word';button.textContent=w.text;button.setAttribute('aria-pressed','false');
                    button.addEventListener('click',()=>{showAll=false;selected=selected===i?-1:i;update();});
                    document.getElementById('words').append(button);wordButtons.push(button);
                  });
                  toggle.addEventListener('click',()=>{showAll=!showAll;selected=-1;update();});
                  document.getElementById('print').addEventListener('click',()=>window.print());
                </script>
                </body>
                </html>
                """;
        return template.replace("{{ROWS}}", Integer.toString(puzzle.grid().length))
                .replace("{{COLS}}", Integer.toString(puzzle.grid()[0].length))
                .replace("{{COUNT}}", Integer.toString(puzzle.placements().size()))
                .replace("{{SEED}}", Long.toString(puzzle.seed()))
                .replace("{{JSON}}", json.replace("<", "\\u003c"));
    }
}

/** Generates and independently checks Russian word-search puzzles. */
final class WordSearchGenerator {
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

/** Draws exact PNG exports directly from a puzzle and its verified placements. */
final class PuzzleRenderer {
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
