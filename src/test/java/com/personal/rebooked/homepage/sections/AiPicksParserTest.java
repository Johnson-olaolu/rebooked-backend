package com.personal.rebooked.homepage.sections;

import com.personal.rebooked.homepage.dto.PickRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiPicksParserTest {
    private static final Set<String> POOL = Set.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j");

    private static String picks(String... ids) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"id\":\"").append(ids[i]).append("\",\"reason\":\"Because ").append(ids[i]).append("\"}");
        }
        return sb.append(']').toString();
    }

    @Test
    void parsesAPlainArray() {
        List<PickRef> picks = AiPicksParser.parse(picks("a", "b", "c", "d"), POOL);
        assertThat(picks).extracting(PickRef::id).containsExactly("a", "b", "c", "d");
        assertThat(picks.get(0).reason()).isEqualTo("Because a");
    }

    @Test
    void acceptsACodeFenceAroundTheArray() {
        String response = "Here you go:\n```json\n" + picks("a", "b", "c", "d") + "\n```";
        assertThat(AiPicksParser.parse(response, POOL)).hasSize(4);
    }

    @Test
    void dropsUnknownAndRepeatedIds() {
        List<PickRef> picks = AiPicksParser.parse(picks("a", "zz", "b", "a", "c", "d"), POOL);
        assertThat(picks).extracting(PickRef::id).containsExactly("a", "b", "c", "d");
    }

    @Test
    void fewerThanFourValidPicksIsAFailure() {
        assertThatThrownBy(() -> AiPicksParser.parse(picks("a", "b", "zz", "yy", "xx"), POOL))
                .isInstanceOf(AiPicksParser.InvalidResponse.class);
    }

    @Test
    void invalidJsonIsAFailure() {
        assertThatThrownBy(() -> AiPicksParser.parse("[{\"id\": \"a\",]", POOL)).isInstanceOf(AiPicksParser.InvalidResponse.class);
        assertThatThrownBy(() -> AiPicksParser.parse("no json here", POOL)).isInstanceOf(AiPicksParser.InvalidResponse.class);
        assertThatThrownBy(() -> AiPicksParser.parse(null, POOL)).isInstanceOf(AiPicksParser.InvalidResponse.class);
    }

    @Test
    void longReasonsAreTrimmedTo90Characters() {
        String reason = "x".repeat(150);
        String response = "[{\"id\":\"a\",\"reason\":\"" + reason + "\"},{\"id\":\"b\"},{\"id\":\"c\"},{\"id\":\"d\"}]";
        List<PickRef> picks = AiPicksParser.parse(response, POOL);
        assertThat(picks.get(0).reason()).hasSize(AiPicksParser.REASON_MAX);
        assertThat(picks.get(1).reason()).isEmpty();
    }

    @Test
    void keepsAtMostEightPicks() {
        assertThat(AiPicksParser.parse(picks("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"), POOL)).hasSize(8);
    }
}
