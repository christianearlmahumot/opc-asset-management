package edu.citu.opc.fams.document;

import java.util.List;
import java.util.stream.Collectors;

/** What Tesseract read: pages of lines of words, each with its box (pixels) and confidence (0-100). */
final class OcrText {

    private OcrText() {
    }

    record Word(String text, int x, int y, int w, int h, int conf) {
        int right() { return x + w; }
        int centerX() { return x + w / 2; }
        int centerY() { return y + h / 2; }
    }

    record Line(List<Word> words) {
        String text() {
            return words.stream().map(Word::text).collect(Collectors.joining(" "));
        }

        int conf() {
            return (int) Math.round(words.stream().mapToInt(Word::conf).average().orElse(0));
        }

        int y() {
            return words.stream().mapToInt(Word::y).min().orElse(0);
        }
    }

    record Page(int number, int width, int height, List<Line> lines) {
    }

    /** Plain text of every page, kept with the document for reference. */
    static String plain(List<Page> pages) {
        StringBuilder sb = new StringBuilder();
        for (Page p : pages) {
            if (pages.size() > 1) sb.append("--- page ").append(p.number()).append(" ---\n");
            for (Line l : p.lines()) sb.append(l.text()).append('\n');
        }
        return sb.toString();
    }
}
