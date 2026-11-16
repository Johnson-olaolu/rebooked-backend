package com.personal.rebooked.rating.dto;

/** PUT /book/{id}/rating. Validated in RatingService so the errors use the form's field keys. */
public record RatePayload(Integer stars, String comment) {
}
