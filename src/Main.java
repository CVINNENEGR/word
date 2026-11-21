import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        Path words = Path.of("words.txt");
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

                java -cp build Main [параметры]
                  --size 10x10       Строки x столбцы. Также 11x10, 10x11, 12x10, 10x12.
                  --seed 42          Начальное значение генератора случайных чисел.
                  --words words.txt  UTF-8: одно русское слово на строку, минимум 10 разных.
                  --out output       Папка для TXT, JSON, HTML и двух PNG-изображений.
                  --help             Эта справка.

                Значения в примере — значения по умолчанию. Координаты в консоли с 1, в JSON с 0.
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
