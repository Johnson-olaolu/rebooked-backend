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
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.data.mongodb.core.aggregation.AggregationOperation;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.lookup;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.unwind;

@RequiredArgsConstructor
@Service
public class BookService {

    private final BookRepository bookRepository;
    private final FileService fileService;
    private final CategoryService categoryService;
    private final MongoTemplate mongoTemplate;

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

//    public Page<Book> query(QueryBookDTO queryBookDTO) {
//
//
//        List<AggregationOperation> operations = new ArrayList<>();
//
//        // 1. Lookup to join with categories
//        operations.add(LookupOperation.newLookup()
//                .from("category")
//                .localField("categories")
//                .foreignField("_id")
//                .as("categoryDetails"));
//
//        operations.add(Aggregation.unwind("categoryDetails"));
//
//        // Create criteria builder
//        Criteria criteria = new Criteria();
//        List<Criteria> orCriteria = new ArrayList<>();
//
//        // 1. Handle userId filter
//        if (queryBookDTO.userId() != null && !queryBookDTO.userId().isEmpty()) {
//            criteria.and("user.$id").is(new ObjectId(queryBookDTO.userId()));
//        }
//
//        // 2. Handle search across multiple fields
//        if (queryBookDTO.search() != null && !queryBookDTO.search().trim().isEmpty()) {
//            String searchRegex = ".*" + Pattern.quote(queryBookDTO.search().trim()) + ".*";
//            Pattern pattern = Pattern.compile(searchRegex, Pattern.CASE_INSENSITIVE);
//
//            // Search in title
//            orCriteria.add(Criteria.where("title").regex(pattern));
//
//            // Search in author
//            orCriteria.add(Criteria.where("author").regex(pattern));
//
//            // Search in category names
////            orCriteria.add(Criteria.where("categories").elemMatch(
////                    Criteria.where("name").regex(pattern)
////            ));
//            orCriteria.add(Criteria.where('categoryDetails.name').regex(pattern))
//
//            if (!orCriteria.isEmpty()) {
//                criteria.orOperator(orCriteria.toArray(new Criteria[0]));
//            }
//        }
//
//        // 3. Handle category filter
//        if (!queryBookDTO.categoryIds().isEmpty()) {
//            List<ObjectId> categoryObjectIds = queryBookDTO.categoryIds().stream()
//                    .map(ObjectId::new)
//                    .collect(Collectors.toList());
//
//            criteria.and("categories.$id").in(categoryObjectIds);
//        }
//
//        // 4. Create query with criteria
//        Query query = Query.query(criteria);
//
//        // Add pagination
//        Pageable pageable = PageRequest.of(queryBookDTO.page(), queryBookDTO.pageSize());
//        long total = mongoTemplate.count(query, Book.class);
//        query.with(pageable); // Apply pagination and sorting
//
//        // Execute and return paginated results
//        List<Book> books = mongoTemplate.find(query, Book.class);
//        return new PageImpl<>(books, pageable, total);
//    }


    public Page<Book> query(QueryBookDTO queryDTO) {

        List<AggregationOperation> operations = new ArrayList<>();

        operations.add(lookup("category", "categories.$id", "_id", "categoryDetails"));
        // 2. Match stage for filtering
        List<Criteria> criteria = new ArrayList<>();

        // Handle userId filter
        if (queryDTO.userId() != null && !queryDTO.userId().isEmpty()) {
            criteria.add(Criteria.where("user.$id").is(new ObjectId(queryDTO.userId())));
        }

        // Handle search
        if (queryDTO.search() != null && !queryDTO.search().trim().isEmpty()) {
            String searchRegex = ".*" + Pattern.quote(queryDTO.search().trim()) + ".*";
            Pattern pattern = Pattern.compile(searchRegex, Pattern.CASE_INSENSITIVE);

            List<Criteria> searchCriteria = new ArrayList<>();
            // Search in title
            searchCriteria.add(Criteria.where("title").regex(pattern));
            // Search in author
            searchCriteria.add(Criteria.where("author").regex(pattern));
            // Search in category names
            searchCriteria.add(Criteria.where("categoryDetails.name").regex(pattern));

            criteria.add(new Criteria().orOperator(searchCriteria.toArray(new Criteria[0])));
        }

        // Handle category filter
        if (!queryDTO.categoryIds().isEmpty()) {
            List<ObjectId> categoryObjectIds = queryDTO.categoryIds().stream()
                    .map(ObjectId::new)
                    .collect(Collectors.toList());

            criteria.add(Criteria.where("categories.$id").in(categoryObjectIds));
        }

        // Add match stage if there are any criteria
        if (!criteria.isEmpty()) {
            operations.add(Aggregation.match(
                    new Criteria().andOperator(criteria.toArray(new Criteria[0]))
            ));
        }

        // 3. Count total documents for pagination
        Aggregation countAgg = Aggregation.newAggregation(operations);
        List<Book> matchingBooks = mongoTemplate.aggregate(countAgg, "book", Book.class).getMappedResults();
        long total = matchingBooks.size();

        // 4. Add pagination
        operations.add(Aggregation.skip((long) queryDTO.page() * queryDTO.pageSize()));
        operations.add(Aggregation.limit(queryDTO.pageSize()));

        // 5. Execute final aggregation
        Aggregation aggregation = Aggregation.newAggregation(operations);
        List<Book> books = mongoTemplate.aggregate(aggregation, "book", Book.class)
                .getMappedResults();


        return new PageImpl<>(
                books,
                PageRequest.of(queryDTO.page(), queryDTO.pageSize()),
                total
        );
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
