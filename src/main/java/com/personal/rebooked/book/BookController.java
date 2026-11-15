package com.personal.rebooked.book;

import com.personal.rebooked.book.dto.*;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.security.CurrentUser;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.utils.Constants;
import com.personal.rebooked.utils.ResponseHandler;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RequestMapping("/book")
@RestController
@SecurityRequirement(name = "bearerAuth")
public class BookController {
    private final BookService bookService;

    @PostMapping
    public ResponseEntity<Object> createBook(@RequestBody @Valid CreateBookDto createBookDto) {
        Book book = bookService.create(CurrentUser.require(), createBookDto);
        return ResponseHandler.generateResponse(book, "Book created successfully", HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<Object> getAllBooks(@RequestParam(required = false) Constants.BookStatus status) {
        // Drafts are private to their owner (see GET /book/me)
        List<Book> books = status == Constants.BookStatus.DRAFT ? List.of()
                : bookService.findAll(status).stream()
                        .filter(b -> b.getStatus() != Constants.BookStatus.DRAFT).toList();
        return ResponseHandler.generateResponse(books, "Books fetched successfully");
    }

    /** My shelf: the caller's books, any status (including DRAFT), newest change first. */
    @GetMapping("/me")
    public ResponseEntity<Object> myShelf(@RequestParam(required = false) Constants.BookStatus status,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer pageSize) {
        ShelfResponse shelf = bookService.shelf(CurrentUser.require(), status, page, pageSize);
        return ResponseHandler.generateResponse(shelf, "Books fetched successfully");
    }

    @GetMapping("/query")
    public ResponseEntity<Object> queryBooks(@ParameterObject @ModelAttribute @Valid QueryBookDTO queryBookDTO) {
        PageResponse<Book> books = bookService.query(queryBookDTO);
        return ResponseHandler.generateResponse(books, "Books fetched successfully");
    }

    @GetMapping("/sold")
    public ResponseEntity<Object> queryBooksSold(@ParameterObject @ModelAttribute @Valid QuerySoldBooksDTO querySoldBooksDTO) {
        List<Book> books = bookService.getSoldBooks(querySoldBooksDTO);
        return ResponseHandler.generateResponse(books, "Books fetched successfully");
    }

    @GetMapping("/ai-picks")
    public ResponseEntity<Object> getAIPicks() {
        List<Book> aiPicks = bookService.getAIRecommendedBooks();
        return ResponseHandler.generateResponse(aiPicks, "AI Picks fetched successfully");
    }

    @GetMapping("/most-viewed")
    public ResponseEntity<Object> getMostViewedBooks() {
        List<Book> mostViewedBooks = bookService.getTopBooks();
        return ResponseHandler.generateResponse(mostViewedBooks, "Most viewed books fetched successfully");
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> getBookById(@PathVariable String id) {
        Book book = bookService.findVisible(id, CurrentUser.optional().orElse(null));
        return ResponseHandler.generateResponse(book, "Book fetched successfully");
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Object> updateBook(@PathVariable String id, @RequestBody @Valid UpdateBookDTO updateBookDTO) {
        Book book = bookService.update(CurrentUser.require(), id, updateBookDTO);
        return ResponseHandler.generateResponse(book, "Book updated successfully");
    }

    @PatchMapping("/{bookId}/view")
    public ResponseEntity<Object> viewBook(@PathVariable String bookId, HttpServletRequest request) {
        String viewer = CurrentUser.optional()
                .map(u -> "user:" + u.getId())
                .orElseGet(() -> "ip:" + clientIp(request));
        Book book = bookService.viewed(bookId, viewer);
        return ResponseHandler.generateResponse(book, "Book updated successfully");
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<Object> updateBookStatus(@PathVariable String id,
            @RequestBody @Valid UpdateBookStatusDTO updateBookStatusDTO) {
        Book book = bookService.updateStatus(CurrentUser.require(), id, updateBookStatusDTO);
        return ResponseHandler.generateResponse(book, "Book status updated successfully");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteBook(@PathVariable String id) {
        bookService.delete(CurrentUser.require(), id);
        return ResponseHandler.generateResponse(null, "Book deleted successfully");
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
