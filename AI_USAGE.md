# AI Usage Report

This document describes how AI assistance (large language model) was used
during the development of this project. It is intended to satisfy the
assignment requirement to disclose AI involvement and to preserve a readable
record of the assistance. The actual prompt/response transcripts are kept
alongside this file in `ai-transcripts/` (not committed to the public repo;
available on request for grading).

The project is **variant 5, "Words"**: a Java 17 generator for Russian word-search
puzzles with a randomized backtracking placement algorithm, an independent
validator, and four export formats (TXT, JSON, HTML, PNG).

---

## 1. Scope of AI involvement

AI was used as a **design and review assistant**, not as a code generator that
produced the final files end-to-end. Concretely, AI contributed to:

- discussing the algorithmic approach (randomized backtracking vs. greedy
  placement, how to enforce "exactly one occurrence" in the finished grid);
- proposing the incremental `Window` / `Link` data structure and the
  `byCell` index used to update only the windows affected by a cell change;
- reviewing the backtracking search for correctness of rollback
  (`setCell` / `clearCell` symmetry);
- reviewing the `validate` method for missing cases (palindromes counted twice,
  zero-based vs. one-based coordinates, `Ё` vs `Е`);
- suggesting the `SegmentKey` normalization for palindrome de-duplication;
- reviewing the HTML export for `</script>` injection risk and recommending
  the `json.replace("<", "\u003c")` guard;
- reviewing the AWT rendering code for headless compatibility and for
  drawing highlights *before* letters so glyphs stay readable;
- suggesting edge cases for the test suite (duplicate words, Latin letters,
  fewer than 10 words, overlong words, damaged grid detection).

All final code was written, integrated, compiled, and tested by the project
author. AI suggestions were accepted, rejected, or rewritten based on actual
test runs.

## 2. What AI did NOT do

- It did not produce the final source files verbatim. The code in `src/` is
  the result of manual integration and multiple review passes.
- It did not run the program. All test results reported in `VALIDATION.txt`
  come from actual local runs.
- It did not generate the bundled PNG exports; those are produced by
  `PuzzleRenderer.java` at runtime.
- It did not write the Russian word list in `words.txt` from a dictionary
  service; the list is a hand-picked sample for the assignment.

## 3. Algorithmic decisions and AI's role

### 3.1 Randomized backtracking instead of greedy placement
**AI's contribution:** comparison of greedy placement (place each word once,
never backtrack) against backtracking. Greedy is simpler but fails on dense
inputs because an early bad choice cannot be undone. Backtracking was chosen;
AI outlined the recursive skeleton and the budget concept.

**Author's contribution:** concrete implementation of `placeWords()`, the
choice to pick the *longest* unmatched word first, the direction-coverage
pruning (`bitCount(ALL_DIRECTIONS & ~coverage) > unmatched`), and the ordering
of candidates (new orientations first, then most reused letters).

### 3.2 Incremental occurrence tracking
**AI's contribution:** the idea of precomputing all possible word windows
(position × direction) and maintaining `empty` / `mismatches` counters per
window, updated only through `byCell`.

**Author's contribution:** the exact `Link(window, position)` record, the
`setCell` / `clearCell` symmetry, and the `duplicatedWords` counter that
increments on the 1→2 transition of `occurrenceCounts[wordIndex]`.

### 3.3 Filling without creating duplicates
**AI's contribution:** the idea of forbidding, in a near-complete window, the
letter that would complete it.

**Author's contribution:** the MRV selection over `fillOrder`, the bit-mask
representation of forbidden letters (`long[] forbidden`), and the interaction
between `forbidden` and the `duplicatedWords` check after each tentative letter.

### 3.4 "Exactly once" semantics
**AI's contribution:** the observation that palindrome words read in both
directions along the same segment are one physical occurrence, not two.

**Author's contribution:** the `SegmentKey(min, max)` normalization and its
use both in `naiti_vhozhdeniya` (de-duplication) and in `proverka_correctnosti`
(verifying that metadata coordinates match the actual occurrence).

### 3.5 Validation
**AI's contribution:** a checklist of invariants (grid shape, alphabet, unique
words, direction range, in-bounds, letters match, exactly one occurrence,
all three orientations present).

**Author's contribution:** implementation of `proverka_correctnosti`, the
decision to re-scan the finished letters rather than trust `placements`, and
the error messages.

## 4. Non-algorithmic assistance

| Area | AI's role | Author's role |
|---|---|---|
| `Main.java` CLI parsing | suggested `Locale.ROOT` in `toUpperCase` and `String.format` | wrote the parser, chose the `Options` record |
| JSON export | pointed out the `</script>` risk | wrote `ekranirovat` and the manual JSON builder |
| HTML export | suggested the `replace("<", "\u003c")` guard | wrote the template and the embedded JS |
| PNG rendering | suggested drawing highlights before letters | wrote `PuzzleRenderer.java`, chose the layout |
| Build scripts | none | `run.sh` / `run.bat` written by the author |
| Tests | suggested edge cases | wrote `tests/GeneratorTest.java` and ran it |
| README | none | written by the author |

## 5. Record keeping

Per the assignment, a readable transcript of prompts and model responses is
preserved. It is not included in this repository because it contains draft
code that was later superseded. The transcript is kept as a separate archive
and can be provided on request. It covers:

- initial problem statement and constraints;
- discussion of greedy vs. backtracking;
- review of the `Window` / `Link` structure;
- review of `validate` for palindrome handling;
- review of the HTML export for script injection;
- edge cases for the test suite.

No AI-generated text was copied into the final submission without review.

## 6. Reproducibility

All reported results can be reproduced locally:

```sh
./run.sh --size 10x10 --seed 42 --words words.txt --out output
./test.sh