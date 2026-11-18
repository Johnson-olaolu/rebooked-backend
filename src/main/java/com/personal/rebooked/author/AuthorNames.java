package com.personal.rebooked.author;

import java.text.Normalizer;
import java.util.*;

/**
 * The only author-name normaliser (§23a). Author creation, search, proposals, the import, aliases and duplicate
 * checks all go through it, so they agree on what "the same name" means.
 */
public final class AuthorNames {
    public static final int MAX_SEARCH_KEYS = 40;

    /** Latin letters that NFD doesn't decompose into a base letter plus an accent. */
    private static final Map<Character, String> UNDECOMPOSABLE = Map.of(
            'ø', "o", 'æ', "ae", 'ł', "l", 'ß', "ss", 'đ', "d", 'œ', "oe", 'ð', "d", 'þ', "th", 'ı', "i");

    private AuthorNames() {}

    /**
     * NFKC, trim, collapse whitespace, lower case. Punctuation stays: "CHINUA   Achebe" becomes "chinua achebe".
     */
    public static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFKC);
        return n.replaceAll("[\\s\\p{Z}]+", " ").trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Accents stripped, punctuation turned into spaces, whitespace collapsed, lower case:
     * "Ngũgĩ wa Thiong'o" becomes "ngugi wa thiong o". Non-Latin letters are kept as they are.
     */
    public static String fold(String s) {
        if (s == null) return "";
        String lower = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder mapped = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            String replacement = UNDECOMPOSABLE.get(c);
            if (replacement != null) mapped.append(replacement);
            else mapped.append(c);
        }
        String folded = Normalizer.normalize(mapped, Normalizer.Form.NFD)
                // Accents on Latin letters only: other scripts' marks (й, Devanagari vowel signs) are part of the letter
                .replaceAll("(?<=\\p{IsLatin})\\p{M}+", "")
                .replaceAll("[^\\p{L}\\p{N}\\p{M}]+", " ")
                .trim();
        return Normalizer.normalize(folded, Normalizer.Form.NFC);
    }

    /**
     * For the name and each alias: the folded string plus every word-suffix ("chinua achebe" gives
     * "chinua achebe" and "achebe"). Deduplicated, name first, at most {@value #MAX_SEARCH_KEYS} keys.
     */
    public static List<String> searchKeys(String name, Collection<String> aliases) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        addSuffixes(fold(name), keys);
        if (aliases != null) {
            for (String alias : aliases) {
                if (keys.size() >= MAX_SEARCH_KEYS) break;
                addSuffixes(fold(alias), keys);
            }
        }
        return new ArrayList<>(keys);
    }

    private static void addSuffixes(String folded, Set<String> keys) {
        if (folded.isEmpty()) return;
        String[] words = folded.split(" ");
        for (int i = 0; i < words.length && keys.size() < MAX_SEARCH_KEYS; i++) {
            keys.add(String.join(" ", Arrays.copyOfRange(words, i, words.length)));
        }
    }

    /** Trimmed, with runs of whitespace collapsed: how a display name is stored. */
    public static String clean(String s) {
        if (s == null) return null;
        return Normalizer.normalize(s, Normalizer.Form.NFKC).replaceAll("[\\s\\p{Z}]+", " ").trim();
    }

    /** True when {@code folded} starts with {@code q} at a word boundary after the first word. */
    static boolean laterWordStartsWith(String folded, String q) {
        int from = 0;
        while (true) {
            int space = folded.indexOf(' ', from);
            if (space < 0) return false;
            if (folded.startsWith(q, space + 1)) return true;
            from = space + 1;
        }
    }
}
