package com.personal.rebooked.author.importer;

import com.personal.rebooked.security.CurrentUser;
import com.personal.rebooked.utils.ApiResponseDocs;
import com.personal.rebooked.utils.ResponseHandler;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** The admin Imports page (§23f/§23g). SecurityConfig restricts /admin/** to superAdmin. */
@RestController
@RequestMapping("/admin/author-import")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AuthorImportController {
    private final AuthorImportService importService;

    public record StartImportPayload(String source) {}

    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ImportPageResponse.class)))
    @GetMapping
    public ResponseEntity<Object> list(@RequestParam(required = false) Integer page,
                                       @RequestParam(required = false) Integer pageSize) {
        return ResponseHandler.generateResponse(importService.list(page, pageSize), "Imports fetched successfully");
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ImportJobResponse.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<Object> get(@PathVariable String id) {
        return ResponseHandler.generateResponse(importService.get(id), "Import fetched successfully");
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ImportErrorPageResponse.class))),
            @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class)))
    })
    @GetMapping("/{id}/errors")
    public ResponseEntity<Object> errors(@PathVariable String id,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer pageSize) {
        return ResponseHandler.generateResponse(importService.errors(id, page, pageSize), "Import errors fetched successfully");
    }

    /** { "source": "OPEN_LIBRARY" | "WIKIDATA" }. 202 with the queued job; 409 if one is already running. */
    @ApiResponses({
            @ApiResponse(responseCode = "202", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ImportJobResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class))),
            @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<Object> start(@RequestBody(required = false) StartImportPayload payload) {
        AuthorImportJob job = importService.start(payload == null ? null : payload.source(), CurrentUser.require().getId());
        return ResponseHandler.generateResponse(job, "Import started", HttpStatus.ACCEPTED);
    }
}
