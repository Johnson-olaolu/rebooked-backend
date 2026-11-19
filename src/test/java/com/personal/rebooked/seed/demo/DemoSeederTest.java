package com.personal.rebooked.seed.demo;

import com.personal.rebooked.rating.SellerRating;
import com.personal.rebooked.utils.Constants.BookCondition;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class DemoSeederTest {

    @Test
    void onlyLocalMongoUrisAreAllowed() {
        assertThat(DemoSeeder.isLocal("mongodb://localhost:27017/rebooked")).isTrue();
        assertThat(DemoSeeder.isLocal("mongodb://root:secret@127.0.0.1:27017/rebooked?authSource=admin")).isTrue();
        assertThat(DemoSeeder.isLocal("mongodb://mongo:27017/rebooked")).isTrue();
        assertThat(DemoSeeder.isLocal("mongodb://[::1]:27017/rebooked")).isTrue();
        assertThat(DemoSeeder.isLocal("mongodb://localhost:27017,mongo:27018/rebooked?replicaSet=rs0")).isTrue();

        assertThat(DemoSeeder.isLocal("mongodb+srv://user:pw@cluster0.abcde.mongodb.net/rebooked")).isFalse();
        assertThat(DemoSeeder.isLocal("mongodb://user:pw@db.example.com:27017/rebooked")).isFalse();
        assertThat(DemoSeeder.isLocal("mongodb://localhost:27017,10.0.0.5:27017/rebooked")).isFalse();
        assertThat(DemoSeeder.isLocal("")).isFalse();
        assertThat(DemoSeeder.isLocal(null)).isFalse();
    }

    @Test
    void authorSlugsFollowTheAuthorServiceScheme() {
        Set<String> taken = new HashSet<>(Set.of("chinua-achebe", "chinua-achebe-2"));
        assertThat(DemoSeeder.uniqueSlug("chinua-achebe", taken)).isEqualTo("chinua-achebe-3");
        assertThat(DemoSeeder.uniqueSlug("wole-soyinka", taken)).isEqualTo("wole-soyinka");
        assertThat(DemoSeeder.uniqueSlug("wole-soyinka", taken)).isEqualTo("wole-soyinka-2");
    }

    @Test
    void biographyListsUpToThreeTitles() {
        DemoFixture.Work a = work("Things Fall Apart", 1958), b = work("Arrow of God", 1964),
                c = work("No Longer at Ease", null), d = work("Anthills of the Savannah", 1987);
        assertThat(DemoSeeder.biography("Chinua Achebe", List.of(a, b, c, d))).isEqualTo(
                "Chinua Achebe is the author of Things Fall Apart, Arrow of God and No Longer at Ease, "
                        + "with work first published as early as 1958.");
        assertThat(DemoSeeder.biography("X", List.of(c))).isEqualTo("X is the author of No Longer at Ease.");
        assertThat(DemoSeeder.biography("X", List.of())).isNull();
    }

    private static DemoFixture.Work work(String title, Integer year) {
        return new DemoFixture.Work("/works/" + title, title, List.of(), 1, null, year, null, List.of(), List.of());
    }

    @Test
    void bookSlugsAreUnique() {
        Set<String> taken = new HashSet<>();
        Random random = new Random(1);
        for (int i = 0; i < 3000; i++) DemoSeeder.bookSlug("Things Fall Apart", taken, random);
        assertThat(taken).hasSize(3000).allMatch(s -> s.matches("things-fall-apart-\\d{4,6}"));
    }

    @Test
    void ratingSummaryMatchesTheIncrementalShape() {
        Document summary = DemoSeeder.ratingSummary(List.of(5, 4, 4, 4));
        assertThat(summary.getInteger("count")).isEqualTo(4);
        assertThat(summary.getInteger("total")).isEqualTo(17);
        // 4.25 rounds half-even like Mongo's $round
        assertThat(summary.getDouble("average")).isEqualTo(4.2);
        assertThat(summary.get("stars", Document.class))
                .containsEntry("1", 0).containsEntry("4", 3).containsEntry("5", 1).hasSize(5);
        assertThat(DemoSeeder.ratingSummary(List.of(5, 4)).getDouble("average")).isEqualTo(4.5);
    }

    @Test
    void ratingSummariesAreGroupedBySeller() {
        SellerRating a = rating("s1", 5), b = rating("s1", 3), c = rating("s2", 4);
        Map<String, Document> summaries = DemoSeeder.ratingSummaries(List.of(a, b, c));
        assertThat(summaries).containsOnlyKeys("s1", "s2");
        assertThat(summaries.get("s1").getDouble("average")).isEqualTo(4.0);
    }

    @Test
    void pricesAreNairaInStepsOf500WithinBounds() {
        Random random = new Random(7);
        for (int i = 0; i < 2000; i++) {
            BookCondition condition = BookCondition.values()[i % 4];
            double ordinary = DemoSeeder.price(random.nextInt(2000), "Poetry", condition, false, random);
            assertThat(ordinary).isBetween((double) DemoSeeder.MIN_PRICE, (double) DemoSeeder.MAX_PRICE);
            assertThat(ordinary % 500).isZero();
            double textbook = DemoSeeder.price(random.nextInt(2000), "Textbooks", condition, false, random);
            assertThat(textbook).isBetween((double) DemoSeeder.MIN_PRICE, (double) DemoSeeder.MAX_PREMIUM_PRICE);
        }
        assertThat(DemoSeeder.price(null, null, BookCondition.GOOD, false, random)).isPositive();
    }

    @Test
    void listingsUseTheBudgetAndRepeatOnlyPopularWorks() {
        double[] weights = new double[1000];
        for (int i = 0; i < weights.length; i++) weights[i] = 1.0 / (i + 1);
        List<Integer> chosen = DemoSeeder.chooseListings(weights, 600, new Random(3));
        assertThat(chosen).hasSize(600);
        // 450 distinct works (75%), all from the most popular ones
        assertThat(new HashSet<>(chosen)).hasSize(450).allMatch(i -> i < 450);

        assertThat(DemoSeeder.chooseListings(weights, 600, new Random(3))).isEqualTo(chosen);
    }

    @Test
    void popularityFallsWithRankAndFavoursAfricanLiterature() throws Exception {
        String json = """
                [{"workKey":"/works/1","title":"A","authors":[{"name":"X","olKey":"OL1A"}],"coverId":1,"categories":["Poetry"],"subjects":["Love"]},
                 {"workKey":"/works/2","title":"B","authors":[{"name":"Y","olKey":"OL2A"}],"coverId":2,"categories":["Poetry"],"subjects":[]},
                 {"workKey":"/works/3","title":"C","authors":[{"name":"Z","olKey":"OL3A"}],"coverId":3,"categories":["Poetry"],"subjects":["Nigerian poetry"]},
                 {"workKey":"/works/4","title":"No cover","authors":[{"name":"Z"}],"coverId":0,"categories":["Poetry"]}]
                """;
        List<DemoFixture.Work> works = DemoFixture.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        assertThat(works).extracting(DemoFixture.Work::title).containsExactly("A", "B", "C");
        assertThat(works.get(2).african()).isTrue();
        assertThat(works.get(0).coverUrl()).isEqualTo("https://covers.openlibrary.org/b/id/1-L.jpg");

        double[] weights = DemoSeeder.popularity(works);
        assertThat(weights[1]).isLessThan(weights[0]);
        assertThat(weights[2]).isEqualTo(1.0); // third in its category, but boosted 2.5x
    }

    private static SellerRating rating(String seller, int stars) {
        SellerRating r = new SellerRating();
        r.setSellerId(seller);
        r.setStars(stars);
        return r;
    }
}
