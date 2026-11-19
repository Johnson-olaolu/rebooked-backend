package com.personal.rebooked.homepage;

import com.personal.rebooked.exception.ApiException;
import com.personal.rebooked.homepage.dto.FeaturedOverride;
import com.personal.rebooked.homepage.sections.FeaturedSection;
import com.personal.rebooked.security.CurrentUser;
import com.personal.rebooked.utils.ResponseHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/** /admin/homepage/** (superAdmin, via SecurityConfig): featured overrides and on-demand rebuilds (§24d). */
@RestController
@RequestMapping("/admin/homepage")
@RequiredArgsConstructor
public class HomepageAdminController {
    private final FeaturedSection featured;
    private final HomepageService homepageService;
    private final Clock clock;

    @GetMapping("/featured")
    public ResponseEntity<Object> featured(@RequestParam(required = false) String week) {
        return ResponseHandler.generateResponse(featured.adminView(week(week)), "Featured set fetched successfully");
    }

    @PutMapping("/featured")
    public ResponseEntity<Object> override(@RequestBody(required = false) FeaturedOverride body) {
        return ResponseHandler.generateResponse(featured.override(body, CurrentUser.require().getId()),
                "Featured set updated");
    }

    @DeleteMapping("/featured")
    public ResponseEntity<Object> dropOverride(@RequestParam(required = false) String week) {
        return ResponseHandler.generateResponse(featured.dropOverride(week(week)), "Override removed");
    }

    @PostMapping("/refresh/{section}")
    public ResponseEntity<Object> refresh(@PathVariable String section) {
        return ResponseHandler.generateResponse(homepageService.refresh(section), "Rebuilt");
    }

    private LocalDate week(String week) {
        try {
            return HomepageWeek.parse(week, clock);
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", Map.of("week", "Use yyyy-MM-dd"));
        }
    }
}
