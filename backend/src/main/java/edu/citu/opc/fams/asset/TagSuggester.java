package edu.citu.opc.fams.asset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Suggests a property tag in the OPC format year-office-item type-number, e.g. 2018-PCO-PRNT-01
 * (UC-104 step 3). The user can accept or change it; only uniqueness is enforced.
 */
@Service
public class TagSuggester {

    private static final Pattern TRAILING_NUMBER = Pattern.compile("-(\\d+)$");

    private final AssetRepository repo;

    public TagSuggester(AssetRepository repo) {
        this.repo = repo;
    }

    public record Suggestion(String tag, String itemType) {
    }

    @Transactional(readOnly = true)
    public Suggestion suggest(int year, String officeCode, String description, String itemTypeOverride) {
        String office = clean(officeCode == null || officeCode.isBlank() ? "OPC" : officeCode, 10);
        String type = itemTypeOverride != null && !itemTypeOverride.isBlank()
                ? clean(itemTypeOverride, 6)
                : itemType(description);
        String prefix = year + "-" + office + "-" + type + "-";

        int max = 0;
        for (String tag : repo.findTagsStartingWith(prefix)) {
            Matcher m = TRAILING_NUMBER.matcher(tag);
            if (m.find() && tag.length() == prefix.length() + m.group(1).length()) {
                max = Math.max(max, Integer.parseInt(m.group(1)));
            }
        }
        return new Suggestion(prefix + String.format("%02d", max + 1), type);
    }

    /**
     * Item type from the first word of the description: kept as is if 4 letters or fewer, otherwise
     * the first letter plus the next consonants, up to 4: PRINTER → PRNT, MONITOR → MNTR, STOOL → STL.
     */
    static String itemType(String description) {
        if (description == null || description.isBlank()) return "ITEM";
        String word = description.trim().split("[^A-Za-z]+")[0];
        if (word.isEmpty()) {
            String[] parts = description.trim().split("[^A-Za-z]+");
            word = parts.length > 1 ? parts[1] : "ITEM";
        }
        word = word.toUpperCase(Locale.ROOT);
        if (word.length() <= 4) return word;
        StringBuilder sb = new StringBuilder().append(word.charAt(0));
        for (int i = 1; i < word.length() && sb.length() < 4; i++) {
            char c = word.charAt(i);
            if ("AEIOU".indexOf(c) < 0 && c != sb.charAt(sb.length() - 1)) sb.append(c);
        }
        return sb.toString();
    }

    private static String clean(String s, int max) {
        String c = s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return c.isEmpty() ? "X" : c.substring(0, Math.min(max, c.length()));
    }
}
