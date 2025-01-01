package com.personal.rebooked.book;

import com.personal.rebooked.book.dto.*;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.utils.Constants;
import com.personal.rebooked.utils.ResponseHandler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RequiredArgsConstructor
@RequestMapping("/book")
@RestController
public class BookController {
    private final BookService bookService;

    @PostMapping
    public ResponseEntity<Object> createBook(@RequestBody @Valid CreateBookDto createBookDto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) authentication.getPrincipal();
        Book book = bookService.create(currentUser, createBookDto);
        return ResponseHandler.generateResponse(book, "Book created successfully");
    }

    @GetMapping
    public ResponseEntity<Object> getAllBooks(@RequestParam(required = false) Constants.BookStatus status) {
        List<Book> books = bookService.findAll(status);
        return ResponseHandler.generateResponse(books, "Books fetched successfully");
    }

    @GetMapping("/query")
    public ResponseEntity<Object> queryBooks(@ModelAttribute @Valid QueryBookDTO queryBookDTO) {
        Page<Book> books = bookService.query(queryBookDTO);
        return ResponseHandler.generateResponse(books, "Books fetched successfully");
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> getBookById(@PathVariable String id) {
        Book book = bookService.findById(id);
        return ResponseHandler.generateResponse(book, "Book fetched successfully");
    }

    @GetMapping("/sold")
    public ResponseEntity<Object> queryBooksSold(@ModelAttribute @Valid QuerySoldBooksDTO querySoldBooksDTO) {
        List<Book> books = bookService.getSoldBooks(querySoldBooksDTO);
        return ResponseHandler.generateResponse(books, "Books fetched successfully");
    }


    @PatchMapping("/{id}")
    public  ResponseEntity<Object> updateBook( @PathVariable String id, @RequestBody @Valid UpdateBookDTO updateBookDTO) {
        Book book = bookService.update(id, updateBookDTO);
        return ResponseHandler.generateResponse(book, "Book updated successfully");
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<Object> updateBookStatus(@PathVariable String id, @RequestBody @Valid UpdateBookStatusDTO updateBookStatusDTO) {
        Book book = bookService.updateStatus(id, updateBookStatusDTO);
        return ResponseHandler.generateResponse(book, "Book status updated successfully");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteBook(@PathVariable String id) {
        bookService.delete(id);
        return ResponseHandler.generateResponse(null, "Book deleted successfully");
    }
}
