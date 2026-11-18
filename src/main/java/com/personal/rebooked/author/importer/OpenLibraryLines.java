package com.personal.rebooked.author.importer;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.rebooked.author.AuthorNames;
import com.personal.rebooked.author.models.AuthorExternalIdentifier.IdSource;
import com.personal.rebooked.utils.Misc;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsing for Open Library dump lines: tab-separated type, key, revision, last_modified, JSON. Works are read with
 * Jackson's streaming parser (the hot path, tens of millions of lines); one author record at a time is read as a
 * tree. Nothing ever holds a whole file.
 */
public final class OpenLibraryLines {
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(\\d{4})(?!\\d)");
    private static final Pattern AUTHOR_KEY = Pattern.compile("^/authors/OL(\\d+)A$");
    private static final int MAX_ALIASES = 20;
    private static final int MAX_BIOGRAPHY = 10_000;

    private OpenLibraryLines() {}

    public record Line(String type, String key, String json) {}

    /** null when the line doesn't have the five columns. */
    public static Line split(String line) {
        int a = line.indexOf('\t');
        int b = a < 0 ? -1 : line.indexOf('\t', a + 1);
        int c = b < 0 ? -1 : line.indexOf('\t', b + 1);
        int d = c < 0 ? -1 : line.indexOf('\t', c + 1);
        if (d < 0) return null;
        return new Line(line.substring(0, a), line.substring(a + 1, b), line.substring(d + 1));
    }

    /** "/authors/OL123A" → 123; -1 when it isn't an author key. */
    public static long olNumber(String key) {
        if (key == null) return -1;
        Matcher m = AUTHOR_KEY.matcher(key);
        if (!m.matches() || m.group(1).length() > 12) return -1;
        return Long.parseLong(m.group(1));
    }

    /** "/authors/OL123A" → "OL123A". */
    public static String olId(String key) {
        if (key == null) return null;
        int slash = key.lastIndexOf('/');
        return slash < 0 ? key : key.substring(slash + 1);
    }

    /** Every authors[].author.key of a work (and the older authors[].key / authors[].author string forms). */
    public static void workAuthorKeys(JsonFactory factory, String json, Consumer<String> sink) throws IOException {
        try (JsonParser p = factory.createParser(json)) {
            if (p.nextToken() != JsonToken.START_OBJECT) return;
            while (p.nextToken() == JsonToken.FIELD_NAME) {
                String field = p.currentName();
                JsonToken value = p.nextToken();
                if ("authors".equals(field) && value == JsonToken.START_ARRAY) {
                    JsonToken element;
                    while ((element = p.nextToken()) != JsonToken.END_ARRAY && element != null) {
                        if (element == JsonToken.START_OBJECT) readAuthorEntry(p, sink);
                        else p.skipChildren();
                    }
                } else {
                    p.skipChildren();
                }
            }
        }
    }

    private static void readAuthorEntry(JsonParser p, Consumer<String> sink) throws IOException {
        while (p.nextToken() == JsonToken.FIELD_NAME) {
            String field = p.currentName();
            JsonToken value = p.nextToken();
            if ("author".equals(field) && value == JsonToken.START_OBJECT) {
                while (p.nextToken() == JsonToken.FIELD_NAME) {
                    String inner = p.currentName();
                    JsonToken innerValue = p.nextToken();
                    if ("key".equals(inner) && innerValue == JsonToken.VALUE_STRING) sink.accept(p.getText());
                    else p.skipChildren();
                }
            } else if ("author".equals(field) && value == JsonToken.VALUE_STRING) {
                sink.accept(p.getText());
            } else if ("key".equals(field) && value == JsonToken.VALUE_STRING && p.getText().startsWith("/authors/")) {
                sink.accept(p.getText());
            } else {
                p.skipChildren();
            }
        }
    }

    /** An author record mapped to our fields. workCount is filled in from the works pass. */
    public record AuthorRecord(String olId, String name, List<String> aliases, String biography, Integer birthYear,
                               Integer deathYear, String photoUrl, String wikidata, String viaf, String isni,
                               int workCount) {

        public boolean hasStrongId() {
            return wikidata != null || viaf != null || isni != null;
        }

        public AuthorRecord withWorkCount(int count) {
            return new AuthorRecord(olId, name, aliases, biography, birthYear, deathYear, photoUrl, wikidata, viaf, isni, count);
        }

        public Map<IdSource, String> strongIds() {
            Map<IdSource, String> ids = new EnumMap<>(IdSource.class);
            if (wikidata != null) ids.put(IdSource.WIKIDATA, wikidata);
            if (viaf != null) ids.put(IdSource.VIAF, viaf);
            if (isni != null) ids.put(IdSource.ISNI, isni);
            return ids;
        }
    }

    public static AuthorRecord author(ObjectMapper mapper, String key, String json) throws IOException {
        JsonNode node = mapper.readTree(json);
        String name = AuthorNames.clean(text(node, "name"));
        if (name == null || name.isEmpty()) name = AuthorNames.clean(text(node, "personal_name"));
        if (name != null && (name.isEmpty() || name.length() > 200)) name = null;

        LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        String nameKey = name == null ? "" : AuthorNames.normalize(name);
        List<String> candidates = new ArrayList<>();
        JsonNode alternates = node.get("alternate_names");
        if (alternates != null && alternates.isArray()) alternates.forEach(n -> { if (n.isTextual()) candidates.add(n.asText()); });
        candidates.add(text(node, "personal_name"));
        candidates.add(text(node, "fuller_name"));
        for (String candidate : candidates) {
            String alias = AuthorNames.clean(candidate);
            if (alias == null || alias.length() < 2 || alias.length() > 200 || aliases.size() >= MAX_ALIASES) continue;
            String aliasKey = AuthorNames.normalize(alias);
            if (!aliasKey.equals(nameKey)) aliases.putIfAbsent(aliasKey, alias);
        }

        String bio = null;
        JsonNode bioNode = node.get("bio");
        if (bioNode != null && bioNode.isTextual()) bio = bioNode.asText();
        else if (bioNode != null && bioNode.isObject() && bioNode.path("value").isTextual()) bio = bioNode.path("value").asText();
        bio = Misc.stripHtml(bio);
        if (bio != null && bio.length() > MAX_BIOGRAPHY) bio = bio.substring(0, MAX_BIOGRAPHY);

        String photoUrl = null;
        JsonNode photos = node.get("photos");
        if (photos != null && photos.isArray()) {
            for (JsonNode photo : photos) {
                if (photo.canConvertToLong() && photo.asLong() > 0) {
                    photoUrl = "https://covers.openlibrary.org/a/id/" + photo.asLong() + "-M.jpg";
                    break;
                }
            }
        }

        JsonNode remote = node.path("remote_ids");
        return new AuthorRecord(olId(key), name, new ArrayList<>(aliases.values()), bio,
                year(text(node, "birth_date")), year(text(node, "death_date")), photoUrl,
                IdSource.WIKIDATA.normalize(text(remote, "wikidata")),
                IdSource.VIAF.normalize(text(remote, "viaf")),
                IdSource.ISNI.normalize(text(remote, "isni")), 0);
    }

    /** location of a /type/redirect record, e.g. "/authors/OL2A". */
    public static String redirectLocation(ObjectMapper mapper, String json) throws IOException {
        return text(mapper.readTree(json), "location");
    }

    /** The first 4-digit year in a free-text date ("12 March 1930", "c. 1930"). */
    static Integer year(String date) {
        if (date == null) return null;
        Matcher m = YEAR.matcher(date);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }
}
