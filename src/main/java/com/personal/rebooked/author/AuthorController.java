package com.personal.rebooked.author;

import com.personal.rebooked.author.dto.ProposalPayload;
import com.personal.rebooked.security.CurrentUser;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.utils.ApiResponseDocs;
import com.personal.rebooked.utils.Misc;
import com.personal.rebooked.utils.ResponseHandler;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Public author endpoints (§23c). Proposals need a login; everything else is open. */
@RestController
@RequestMapping("/author")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AuthorController {
    private final AuthorService authorService;

    /** The book form's author picker. 60 searches a minute per user (or per IP when logged out). */
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.AuthorListResponse.class))),
            @ApiResponse(responseCode = "400", description = "q shorter than 2 or longer than 80 characters",
                    content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class))),
            @ApiResponse(responseCode = "429", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class)))
    })
    @GetMapping("/search")
    public ResponseEntity<Object> search(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) Integer limit,
                                         HttpServletRequest request) {
        String who = CurrentUser.optional().map(User::getId).map(id -> "user:" + id)
                .orElseGet(() -> "ip:" + Misc.clientIp(request));
        return ResponseHandler.generateResponse(authorService.search(q, limit, who), "Authors fetched successfully");
    }

    /** Most-shelved authors, for the home page. */
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.AuthorListResponse.class)))
    })
    @GetMapping("/top")
    public ResponseEntity<Object> top(@RequestParam(required = false) Integer limit) {
        return ResponseHandler.generateResponse(authorService.top(limit), "Authors fetched successfully");
    }

    /** A merged author answers with the canonical one: swap the URL when the slug differs. */
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.AuthorDetailResponse.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class)))
    })
    @GetMapping("/slug/{slug}")
    public ResponseEntity<Object> bySlug(@PathVariable String slug) {
        return ResponseHandler.generateResponse(authorService.detailBySlug(slug), "Author fetched successfully");
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.AuthorDetailResponse.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<Object> byId(@PathVariable String id) {
        return ResponseHandler.generateResponse(authorService.detailById(id), "Author fetched successfully");
    }

    /**
     * Add a missing author. With close matches and force=false: 200 with the candidates and nothing created.
     * Otherwise 201 with the new (UNVERIFIED, usable at once) author. 20 a day per user.
     */
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Close matches found; nothing created",
                    content = @Content(schema = @Schema(implementation = ApiResponseDocs.ProposalResponse.class))),
            @ApiResponse(responseCode = "201", description = "Author created",
                    content = @Content(schema = @Schema(implementation = ApiResponseDocs.ProposalResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class))),
            @ApiResponse(responseCode = "429", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class)))
    })
    @PostMapping("/proposals")
    public ResponseEntity<Object> propose(@RequestBody(required = false) ProposalPayload payload) {
        AuthorService.ProposalOutcome outcome = authorService.propose(CurrentUser.require().getId(), payload);
        String message = outcome.result().author() == null ? "Similar authors found" : "Author added";
        return ResponseHandler.generateResponse(outcome.result(), message, outcome.status());
    }
}
