package com.personal.rebooked.utils;

import com.personal.rebooked.auth.dto.LoginResponseDTO;
import com.personal.rebooked.author.admin.dto.*;
import com.personal.rebooked.author.dto.AuthorDetail;
import com.personal.rebooked.author.dto.AuthorSummary;
import com.personal.rebooked.author.dto.ProposalResult;
import com.personal.rebooked.author.importer.AuthorImportJob;
import com.personal.rebooked.author.importer.AuthorImportService;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.user.models.User;

import java.util.List;
import java.util.Map;

/**
 * Swagger-only schemas that describe the { message, status, data } envelope.
 * Controllers still return ResponseEntity<Object>; these types are never instantiated.
 */
public final class ApiResponseDocs {
    private ApiResponseDocs() {}

    public record AuthResponse(String message, int status, LoginResponseDTO data) {}

    public record UserResponse(String message, int status, User data) {}

    public record EmptyResponse(String message, int status, Object data) {}

    public record ErrorResponse(int status, String message, Map<String, String> errors) {}

    public record AuthorListResponse(String message, int status, List<AuthorSummary> data) {}

    public record AuthorDetailResponse(String message, int status, AuthorDetail data) {}

    public record ProposalResponse(String message, int status, ProposalResult data) {}

    public record AdminAuthorPageResponse(String message, int status, AdminAuthorPage data) {}

    public record AdminAuthorResponse(String message, int status, AdminAuthorDetail data) {}

    public record ReviewPageResponse(String message, int status, AdminPage<ReviewItemView> data) {}

    public record ReviewItemResponse(String message, int status, ReviewItemView data) {}

    public record AdminBookPageResponse(String message, int status, AdminPage<Book> data) {}

    public record AuditPageResponse(String message, int status, AdminPage<AuditEntryView> data) {}

    public record MergePreviewResponse(String message, int status, MergePreview data) {}

    public record ImportPageResponse(String message, int status, AdminPage<AuthorImportJob> data) {}

    public record ImportJobResponse(String message, int status, AuthorImportJob data) {}

    public record ImportErrorPageResponse(String message, int status, AdminPage<AuthorImportService.ImportErrorView> data) {}
}
