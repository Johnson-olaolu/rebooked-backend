package com.personal.rebooked.category;

import com.mongodb.client.result.UpdateResult;
import com.personal.rebooked.category.dto.CreateCategoryDTO;
import com.personal.rebooked.category.dto.UpdateCategoryDTO;
import com.personal.rebooked.category.models.Category;
import com.personal.rebooked.category.repositories.CategoryRepository;
import com.personal.rebooked.book.models.Book;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.BasicQuery;

import com.personal.rebooked.exception.ApiException;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.ArithmeticOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import com.personal.rebooked.utils.Constants;
import java.util.List;

@RequiredArgsConstructor
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    public List<Category> findAll() {
        List<Category> categories = categoryRepository.findAll();
        Map<String, Long> counts = countActiveBooksByCategory();
        categories.forEach(c -> c.setBooksCount(counts.getOrDefault(c.getId(), 0L).intValue()));
        return categories;
    }

    /** ACTIVE books per category id, in one aggregation over the book collection. */
    public Map<String, Long> countActiveBooksByCategory() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("status").is(Constants.BookStatus.ACTIVE)),
                Aggregation.unwind("categories"),
                // DBRef ids can't be referenced as "$categories.$id" in expressions; read them via $objectToArray
                ctx -> new Document("$project", new Document("cid", new Document("$arrayElemAt", List.of(
                        new Document("$map", new Document("input", new Document("$objectToArray", "$categories"))
                                .append("in", "$$this.v")), 1)))),
                ctx -> new Document("$group", new Document("_id", "$cid").append("n", new Document("$sum", 1))));
        Map<String, Long> counts = new HashMap<>();
        for (Document row : mongoTemplate.aggregate(aggregation, "book", Document.class).getMappedResults()) {
            if (row.get("_id") != null) counts.put(row.get("_id").toString(), ((Number) row.get("n")).longValue());
        }
        return counts;
    }

    public Category findById(String id) {
        if (id == null || !ObjectId.isValid(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
        }
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
    }

    public Category findByName(String name) {
        return categoryRepository.findByName(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
    }

    public Category create(CreateCategoryDTO createCategoryDTO) {
        String name = createCategoryDTO.name().trim();
        requireUniqueName(name, null);
        Category category = new Category();
        category.setName(name);
        category.setDescription(createCategoryDTO.description());
        category.setImageUrl(createCategoryDTO.imageUrl());
        // Only the seeder (via upsert) creates seeded categories; the client's value is ignored
        category.setIsSeeded(false);
        return categoryRepository.save(category);
    }

    /** 409 if another category already has this name (case-insensitive). */
    private void requireUniqueName(String name, String exceptId) {
        Query query = new Query(Criteria.where("name")
                .regex("^" + java.util.regex.Pattern.quote(name) + "$", "i"));
        if (exceptId != null) query.addCriteria(Criteria.where("_id").ne(new ObjectId(exceptId)));
        if (mongoTemplate.exists(query, Category.class)) {
            throw new ApiException(HttpStatus.CONFLICT, "A category with this name already exists",
                    java.util.Map.of("name", "A category with this name already exists"));
        }
    }

    public void upsert(CreateCategoryDTO createCategoryDTO) {
        Query query = new Query(Criteria.where("name").is(createCategoryDTO.name()));
        Update update = new Update()
                .set("description", createCategoryDTO.description())
                .set("imageUrl", createCategoryDTO.imageUrl())
                .set("isSeeded", createCategoryDTO.isSeeded());

        mongoTemplate.upsert(query, update, Category.class);
    }

    public Category update(String id, UpdateCategoryDTO updateCategoryDTO) {
        Category category = findById(id);
        if (updateCategoryDTO.name() != null && !updateCategoryDTO.name().isBlank()) {
            String name = updateCategoryDTO.name().trim();
            requireUniqueName(name, category.getId());
            category.setName(name);
        }
        return categoryRepository.save(category);
    }

    public List<Category> fetchTopCategories(int limit) {
        List<Category> categories = categoryRepository.findByIsSeededTrue();
        Map<String, Long> counts = countActiveBooksByCategory();
        categories.forEach(c -> c.setBooksCount(counts.getOrDefault(c.getId(), 0L).intValue()));
        return categories.stream()
                .sorted(Comparator.comparing(Category::getBooksCount).reversed())
                .limit(limit)
                .toList();
    }

    public void delete(String id) {
        Category category = findById(id);
        // Remove the reference from every book first so no broken DBRefs are left behind
        Object categoryId = ObjectId.isValid(category.getId()) ? new ObjectId(category.getId()) : category.getId();
        mongoTemplate.updateMulti(new BasicQuery(new Document("categories.$id", categoryId)),
                new Update().pull("categories", new Document("$id", categoryId)), Book.class);
        categoryRepository.delete(category);
    }

}
