package com.personal.rebooked.homepage;

import com.personal.rebooked.homepage.sections.ForYouSection;
import com.personal.rebooked.security.CurrentUser;
import com.personal.rebooked.utils.ResponseHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/** The public home page (§24): one cached bundle, stats for other pages, and personalised picks. */
@RestController
@RequestMapping("/homepage")
@RequiredArgsConstructor
public class HomepageController {
    private static final CacheControl PUBLIC_MINUTE = CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic();

    private final HomepageService homepageService;
    private final ForYouSection forYou;

    @GetMapping
    public ResponseEntity<Object> homepage() {
        return withCache(ResponseHandler.generateResponse(homepageService.bundle(), "Homepage fetched successfully"));
    }

    @GetMapping("/stats")
    public ResponseEntity<Object> stats() {
        return withCache(ResponseHandler.generateResponse(homepageService.stats(), "Stats fetched successfully"));
    }

    @GetMapping("/for-you")
    public ResponseEntity<Object> forYou() {
        return ResponseHandler.generateResponse(forYou.forYou(CurrentUser.require()), "Picks fetched successfully");
    }

    private static ResponseEntity<Object> withCache(ResponseEntity<Object> response) {
        return ResponseEntity.status(response.getStatusCode()).cacheControl(PUBLIC_MINUTE).body(response.getBody());
    }
}
