package com.personal.rebooked.author.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.personal.rebooked.author.models.Author;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** WikidataEnricher against a fake wbgetentities on localhost. */
class WikidataEnricherTest {
    private static final String ENTITIES = """
            {"entities": {"Q155845": {"id": "Q155845", "type": "item",
              "descriptions": {"en": {"language": "en", "value": "Nigerian novelist and poet (1930–2013)"}},
              "aliases": {"en": [{"value": "Albert Chinualumogu Achebe"}, {"value": "Chinua Achebe"}]},
              "claims": {
                "P569": [{"rank": "normal", "mainsnak": {"datavalue": {"value": {"time": "+1930-11-16T00:00:00Z"}}}}],
                "P570": [{"rank": "deprecated", "mainsnak": {"datavalue": {"value": {"time": "+2012-01-01T00:00:00Z"}}}},
                         {"rank": "normal", "mainsnak": {"datavalue": {"value": {"time": "+2013-03-21T00:00:00Z"}}}}],
                "P27": [{"rank": "normal", "mainsnak": {"datavalue": {"value": {"id": "Q1033"}}}},
                        {"rank": "normal", "mainsnak": {"datavalue": {"value": {"id": "Q999999999"}}}}],
                "P18": [{"rank": "normal", "mainsnak": {"datavalue": {"value": "Chinua Achebe (1930).jpg"}}}]},
              "sitelinks": {"enwiki": {"site": "enwiki", "title": "Chinua Achebe"}}},
             "Q404": {"id": "Q404", "missing": ""}}}
            """;

    private HttpServer server;
    private final AtomicReference<String> query = new AtomicReference<>();
    private final AtomicReference<String> userAgent = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();
    private WikidataEnricher enricher;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/w/api.php", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            byte[] body;
            int status;
            if (calls.incrementAndGet() == 1) { // the first call is rate limited, to exercise the retry
                status = 429;
                body = new byte[0];
                exchange.getResponseHeaders().add("Retry-After", "1");
            } else {
                status = 200;
                body = ENTITIES.getBytes(StandardCharsets.UTF_8);
            }
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        enricher = new WikidataEnricher(mock(MongoTemplate.class),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/w/api.php",
                "RebookedAuthorImporter/1.0 (test@example.com)", Duration.ZERO);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void fetchesRetriesAndMapsAnEntity() throws Exception {
        Map<String, JsonNode> entities = enricher.fetch(List.of("Q155845", "Q404"));

        assertThat(calls.get()).isEqualTo(2);
        assertThat(userAgent.get()).isEqualTo("RebookedAuthorImporter/1.0 (test@example.com)");
        assertThat(query.get()).contains("action=wbgetentities").contains("ids=Q155845%7CQ404").contains("maxlag=5");
        assertThat(entities.get("Q404").has("missing")).isTrue();

        WikidataEnricher.Facts facts = WikidataEnricher.Facts.from(entities.get("Q155845"), "Q155845");
        assertThat(facts.description()).isEqualTo("Nigerian novelist and poet (1930–2013)");
        assertThat(facts.birthYear()).isEqualTo(1930);
        assertThat(facts.deathYear()).isEqualTo(2013); // the deprecated statement is ignored
        assertThat(facts.countries()).containsExactly("NG");
        assertThat(facts.wikipedia()).isEqualTo("https://en.wikipedia.org/wiki/Chinua_Achebe");
        assertThat(facts.photoUrl()).isEqualTo("https://commons.wikimedia.org/wiki/Special:FilePath/Chinua_Achebe_%281930%29.jpg");
        assertThat(facts.aliases()).containsExactly("Albert Chinualumogu Achebe", "Chinua Achebe");
    }

    @Test
    void onlyFillsEmptyFieldsOrOnesWikidataSetBefore() throws Exception {
        WikidataEnricher.Facts facts = WikidataEnricher.Facts.from(enricher.fetch(List.of("Q155845")).get("Q155845"), "Q155845");
        Author author = new Author();
        author.setName("Chinua Achebe");
        author.setDescription("Written by an admin");
        author.getFieldSources().put("description", Author.Source.ADMIN);
        author.setBirthYear(1931); // already set (say, from Open Library): Wikidata only fills empty years
        author.getAliases().add(new Author.Alias("Albert Chinualumogu Achebe", "albert chinualumogu achebe", Author.Source.OPEN_LIBRARY));

        Update update = WikidataEnricher.changes(author, facts);

        var set = update.getUpdateObject().get("$set", org.bson.Document.class);
        assertThat(set).doesNotContainKeys("description", "birthYear", "aliases");
        assertThat(set).containsEntry("deathYear", 2013).containsEntry("countries", List.of("NG"))
                .containsEntry("links.wikipedia", "https://en.wikipedia.org/wiki/Chinua_Achebe")
                .containsEntry("links.wikidata", "https://www.wikidata.org/wiki/Q155845");
        assertThat(set).containsKey("photo");
    }

    @Test
    void nothingToChangeGivesNull() throws Exception {
        WikidataEnricher.Facts facts = new WikidataEnricher.Facts("Q1", null, List.of(), null, null, List.of(), null, null);
        Author author = new Author();
        author.setName("Someone");
        author.setLinks(new Author.Links(null, "https://www.wikidata.org/wiki/Q1", null));
        assertThat(WikidataEnricher.changes(author, facts)).isNull();
    }
}
