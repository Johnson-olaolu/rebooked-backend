package com.personal.rebooked.homepage.dto;

import java.util.List;

/** GET /homepage/for-you. personalised is false when the global picks were returned instead. */
public record ForYou(List<AiPick> aiPicks, List<String> basedOn, boolean personalised) {
}
