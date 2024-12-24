package com.personal.rebooked.book;

import com.personal.rebooked.book.dto.*;
import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.book.repositories.BookRepository;
import com.personal.rebooked.category.CategoryService;
import com.personal.rebooked.category.models.Category;
import com.personal.rebooked.file.FileService;
import com.personal.rebooked.file.models.File;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.utils.Constants;
import com.personal.rebooked.utils.Misc;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@RequiredArgsConstructor
@Service
public class BookService {

    private final BookRepository bookRepository;
    private final FileService fileService;
    private final CategoryService categoryService;

    public Book create(User user, CreateBookDto createBookDto) {
        Book book = new Book();
        book.setUser(user);
        book.setAuthor(createBookDto.author());
        book.setTitle(createBookDto.title());
        book.setPrice(createBookDto.price());
        book.setDescription(createBookDto.description());
        if (createBookDto.coverImageId() != null) {
            File file = fileService.getFileById(createBookDto.coverImageId());
            book.setCoverImage(file);
        }
        for (String categoryId : createBookDto.categoryIds()) {
            Category category = categoryService.findById(categoryId);
            if (book.getCategories() == null || book.getCategories().isEmpty()) {
                book.setCategories(new ArrayList<>());
            }
            book.getCategories().add(category);
        }

        for (String imageId : createBookDto.imageIds()) {
            File file = fileService.getFileById(imageId);
            if (book.getImages() == null || book.getImages().isEmpty()) {
                book.setImages(new ArrayList<>());
            }
            book.getImages().add(file);
        }
        return bookRepository.save(book);
    }

    public Book findById(String id) {
        return bookRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));
    }

    public List<Book> findAll(Constants.BookStatus status) {
        if (status != null) {
            return bookRepository.findByStatus(status);
        }
        return bookRepository.findAll();
    }

    public Book update(String id, UpdateBookDTO updateBookDto) {
        Book book = findById(id);

        //Fetch the filelds
        Field[] dtoFields = UpdateBookDTO.class.getDeclaredFields();
        Field[] entityFields = Book.class.getDeclaredFields();

        for (Field dtoField : dtoFields) {
            dtoField.setAccessible(true); // Access private fields
            try {
                Object value = dtoField.get(updateBookDto); // Get value of the field in the DTO
                if (value != null) {
                    for (Field entityField : entityFields) {
                        if (dtoField.getName().equals("coverImageId")) {
                            File file = fileService.getFileById(updateBookDto.coverImageId());
                            book.setCoverImage(file);
                        } else if (dtoField.getName().equals("imageIds")) {
                            List<File> files = new ArrayList<>();
                            ;
                            for (String fileId : updateBookDto.imageIds()) {
                                File file = fileService.getFileById(fileId);
                                files.add(file);
                            }
                            book.setImages(files);
                        } else if (dtoField.getName().equals("categoryIds")) {
                            List<Category> categories = new ArrayList<>();
                            ;
                            for (String categoryId : updateBookDto.categoryIds()) {
                                Category category = categoryService.findById(categoryId);
                                categories.add(category);
                            }
                            book.setCategories(categories);
                        } else if (entityField.getName().equals(dtoField.getName())) {
                            entityField.setAccessible(true); // Access private entity field
                            entityField.set(book, value); // Set the value
                        }
                    }
                }
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            }
        }

        return bookRepository.save(book);
    }

    public Page<Book> query(QueryBookDTO query) {
        Pageable pageable = PageRequest.of(query.page(), query.pageSize());
        return bookRepository.findByTitleAuthorOrCategoryName(query.userId(), query.categoryIds(), query.search(), pageable);
    }

    public Book updateStatus(String id, UpdateBookStatusDTO updateBookStatusDTO) {
        Book book = findById(id);
        book.setStatus(updateBookStatusDTO.status());
        return bookRepository.save(book);
    }

    public List<Book> getSoldBooks(QuerySoldBooksDTO querySoldBooksDTO) {
        Date startDate = Misc.calculateStartDate(querySoldBooksDTO.timeQuery());
        Date endDate = new Date();
        return bookRepository.findBySoldDateBetween(querySoldBooksDTO.userId(), startDate, endDate, Constants.BookStatus.SOLD);
    }

    public void delete(String id) {
        bookRepository.deleteById(id);
    }
}
