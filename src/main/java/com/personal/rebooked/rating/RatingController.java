package com.personal.rebooked.rating;

import com.personal.rebooked.rating.dto.RatePayload;
import com.personal.rebooked.security.CurrentUser;
import com.personal.rebooked.utils.ResponseHandler;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class RatingController {
    private final RatingService ratingService;

    /** The rating form: can I rate this sale, and my rating so far. */
    @GetMapping("/book/{id}/rating")
    public ResponseEntity<Object> get(@PathVariable String id) {
        return ResponseHandler.generateResponse(ratingService.get(CurrentUser.require().getId(), id),
                "Rating fetched successfully");
    }

    @PutMapping("/book/{id}/rating")
    public ResponseEntity<Object> rate(@PathVariable String id, @RequestBody(required = false) RatePayload payload) {
        return ResponseHandler.generateResponse(ratingService.rate(CurrentUser.require().getId(), id, payload),
                "Rating saved");
    }

    /** A seller's reviews. Public. */
    @GetMapping("/user/{id}/ratings")
    public ResponseEntity<Object> list(@PathVariable String id,
                                       @RequestParam(required = false) Integer page,
                                       @RequestParam(required = false) Integer pageSize) {
        return ResponseHandler.generateResponse(ratingService.list(id, page, pageSize), "Ratings fetched successfully");
    }
}
