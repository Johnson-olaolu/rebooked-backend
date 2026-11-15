package com.personal.rebooked.utils;

import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Misc {

    /**
     * Maps user or Google input to a canonical name from {@link Constants#NIGERIAN_STATES}, case-insensitively.
     * "Lagos State" becomes "Lagos"; "Abuja", "FCT" and "Federal Capital Territory" become "Federal Capital Territory".
     * Returns null when the input doesn't match any state.
     */
    public static String normalizeNigerianState(String input) {
        if (input == null) return null;
        String value = input.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
        if (value.isEmpty()) return null;
        if (value.equals("abuja") || value.equals("fct") || value.startsWith("federal capital territory")
                || value.equals("abuja federal capital territory")) {
            return "Federal Capital Territory";
        }
        if (value.endsWith(" state")) {
            value = value.substring(0, value.length() - " state".length()).trim();
        }
        for (String state : Constants.NIGERIAN_STATES) {
            if (state.toLowerCase(java.util.Locale.ROOT).equals(value)) return state;
        }
        return null;
    }

    private static final java.util.regex.Pattern NG_PHONE = java.util.regex.Pattern.compile("^(\\+234|0)([789][01]\\d{8})$");
    private static final java.util.regex.Pattern EMAIL = java.util.regex.Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    /** Accepts 0803… or +234803… (spaces, dashes and brackets ignored). Returns E.164 (+234…) or null if invalid. */
    public static String normalizeNigerianPhone(String input) {
        if (input == null) return null;
        String compact = input.replaceAll("[\\s\\-()]", "");
        java.util.regex.Matcher m = NG_PHONE.matcher(compact);
        return m.matches() ? "+234" + m.group(2) : null;
    }

    public static boolean isValidEmail(String input) {
        return input != null && input.length() <= 254 && EMAIL.matcher(input).matches();
    }

    public  static  String getToken() {
        SecureRandom random = new SecureRandom();
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            token.append(random.nextInt(10));
        }
        return token.toString();
    }

    public static boolean isBcryptHash(String password) {
        String regex = "^\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(password);
        return matcher.matches();
    }

    public static String getStringSizeLengthFile(long size) {

        DecimalFormat df = new DecimalFormat("0.00");

        float sizeKb = 1024.0f;
        float sizeMb = sizeKb * sizeKb;
        float sizeGb = sizeMb * sizeKb;
        float sizeTerra = sizeGb * sizeKb;


        if(size < sizeMb)
            return df.format(size / sizeKb)+ " Kb";
        else if(size < sizeGb)
            return df.format(size / sizeMb) + " Mb";
        else if(size < sizeTerra)
            return df.format(size / sizeGb) + " Gb";

        return "";
    }

    public static Date calculateStartDate(Constants.TimeQuery timeQuery) {
        Calendar calendar = Calendar.getInstance();

        switch (timeQuery) {
            case LAST_WEEK:
                calendar.add(Calendar.WEEK_OF_YEAR, -1);
                break;
            case LAST_TWO_WEEKS:
                calendar.add(Calendar.WEEK_OF_YEAR, -2);
                break;
            case LAST_MONTH:
                calendar.add(Calendar.MONTH, -1);
                break;
            case LAST_SIX_MONTHS:
                calendar.add(Calendar.MONTH, -6);
                break;
            case LAST_YEAR:
                calendar.add(Calendar.YEAR, -1);
                break;
            default:
                throw new IllegalArgumentException("Invalid time range: " + timeQuery);
        }

        return calendar.getTime();
    }

    public  static String generateUUID () {
        UUID uuid = UUID.randomUUID();
        return uuid.toString();
    }


    public static Double generateRandomPrice() {
        Random random = new Random();
        // Generate prices between $5.99 and $29.99
        double price = 5.99 + (random.nextDouble() * 24.0);
        return Math.round(price * 100.0) / 100.0; // Round to 2 decimal places
    }

    public static  <T> T getRandomElement(List<T> list) {
        Random random = new Random();
        return list.get(random.nextInt(list.size()));
    }

    public static List<String> getRandomIds(List<String> inputIds, int min, int max) {
        Random random = new Random();
        int count = random.nextInt(max - min + 1) + min;
        List<String> shuffled = new ArrayList<>(inputIds);
        Collections.shuffle(shuffled, random);
        return shuffled.stream()
                .limit(count)
                .toList();
    }

    /** Strips spaces and dashes; returns a 10-digit (last may be X) or 13-digit ISBN, or null if invalid. */
    public static String normalizeIsbn(String input) {
        if (input == null) return null;
        String s = input.replaceAll("[\\s-]", "").toUpperCase(java.util.Locale.ROOT);
        return s.matches("^\\d{9}[\\dX]$") || s.matches("^\\d{13}$") ? s : null;
    }

    /** "Half of a Yellow Sun!" -> "half-of-a-yellow-sun" (ASCII only, at most 60 characters). */
    public static String slugify(String input) {
        if (input == null) return "book";
        String s = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (s.length() > 60) s = s.substring(0, 60).replaceAll("-+$", "");
        return s.isEmpty() ? "book" : s;
    }
}
