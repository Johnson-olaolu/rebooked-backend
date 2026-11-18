package com.personal.rebooked.author.admin;

import com.personal.rebooked.author.admin.dto.*;
import com.personal.rebooked.security.CurrentUser;
import com.personal.rebooked.utils.ApiResponseDocs;
import com.personal.rebooked.utils.ResponseHandler;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** The admin author area (§23f). SecurityConfig restricts /admin/** to superAdmin. */
@RestController
@RequestMapping("/admin/author")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class))),
        @ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class)))
})
public class AuthorAdminController {
    private final AuthorAdminService adminService;

    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.AdminAuthorPageResponse.class)))
    @GetMapping
    public ResponseEntity<Object> list(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String origin,
                                       @RequestParam(required = false) Integer page,
                                       @RequestParam(required = false) Integer pageSize) {
        return ResponseHandler.generateResponse(adminService.list(q, status, origin, page, pageSize), "Authors fetched successfully");
    }

    /** Oldest first; status defaults to OPEN. */
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ReviewPageResponse.class)))
    @GetMapping("/review")
    public ResponseEntity<Object> review(@RequestParam(required = false) String type,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer pageSize) {
        return ResponseHandler.generateResponse(adminService.reviewItems(type, status, page, pageSize), "Review items fetched successfully");
    }

    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ReviewItemResponse.class)))
    @PostMapping("/review/{itemId}/keep-separate")
    public ResponseEntity<Object> keepSeparate(@PathVariable String itemId,
                                               @RequestBody(required = false) KeepSeparatePayload payload) {
        return ResponseHandler.generateResponse(adminService.keepSeparate(itemId, payload, actorId()), "Kept separate");
    }

    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.AdminAuthorResponse.class)))
    @GetMapping("/{id}")
    public ResponseEntity<Object> detail(@PathVariable String id) {
        return ResponseHandler.generateResponse(adminService.detail(id), "Author fetched successfully");
    }

    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.AdminAuthorResponse.class)))
    @PatchMapping("/{id}")
    public ResponseEntity<Object> patch(@PathVariable String id, @RequestBody(required = false) AuthorPatch patch) {
        return ResponseHandler.generateResponse(adminService.patch(id, patch, actorId()), "Author updated");
    }

    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.AdminAuthorResponse.class)))
    @PostMapping("/{id}/verify")
    public ResponseEntity<Object> verify(@PathVariable String id) {
        return ResponseHandler.generateResponse(adminService.verify(id, actorId()), "Author verified");
    }

    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.AdminAuthorResponse.class)))
    @PostMapping("/{id}/external-ids")
    public ResponseEntity<Object> addExternalId(@PathVariable String id, @RequestBody(required = false) ExternalIdPayload payload) {
        return ResponseHandler.generateResponse(adminService.addExternalId(id, payload, actorId()), "Identifier added");
    }

    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.AdminAuthorResponse.class)))
    @DeleteMapping("/{id}/external-ids/{source}/{sourceId}")
    public ResponseEntity<Object> removeExternalId(@PathVariable String id, @PathVariable String source,
                                                   @PathVariable String sourceId) {
        return ResponseHandler.generateResponse(adminService.removeExternalId(id, source, sourceId, actorId()), "Identifier removed");
    }

    /** Books in any status, drafts included, with their owner's public summary. */
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.AdminBookPageResponse.class)))
    @GetMapping("/{id}/books")
    public ResponseEntity<Object> books(@PathVariable String id,
                                        @RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer pageSize) {
        return ResponseHandler.generateResponse(adminService.books(id, page, pageSize), "Books fetched successfully");
    }

    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.AuditPageResponse.class)))
    @GetMapping("/{id}/audit")
    public ResponseEntity<Object> audit(@PathVariable String id,
                                        @RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer pageSize) {
        return ResponseHandler.generateResponse(adminService.audit(id, page, pageSize), "Audit fetched successfully");
    }

    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.MergePreviewResponse.class)))
    @GetMapping("/{id}/merge-preview")
    public ResponseEntity<Object> mergePreview(@PathVariable String id, @RequestParam(required = false) String intoId) {
        return ResponseHandler.generateResponse(adminService.mergePreview(id, intoId), "Merge preview");
    }

    /** Merges {id} into intoId and returns the surviving author. */
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.AdminAuthorResponse.class)))
    @PostMapping("/{id}/merge")
    public ResponseEntity<Object> merge(@PathVariable String id, @RequestBody(required = false) MergePayload payload) {
        return ResponseHandler.generateResponse(adminService.merge(id, payload, actorId()), "Authors merged");
    }

    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.EmptyResponse.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable String id) {
        adminService.delete(id, actorId());
        return ResponseHandler.generateResponse(null, "Author deleted");
    }

    private static String actorId() {
        return CurrentUser.require().getId();
    }
}
