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

import org.checkerframework.checker.units.qual.A;
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
import java.util.List;

@RequiredArgsConstructor
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    public Category findById(String id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
    }

    public Category findByName(String name) {
        return categoryRepository.findByName(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
    }

    public Category create(CreateCategoryDTO createCategoryDTO) {
        Category category = new Category();
        category.setName(createCategoryDTO.name());
        category.setDescription(createCategoryDTO.description());
        category.setImageUrl(createCategoryDTO.imageUrl());
        category.setIsSeeded(createCategoryDTO.isSeeded());
        return categoryRepository.save(category);
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
        if (updateCategoryDTO.name() != null) {
            category.setName(updateCategoryDTO.name());
        }
        return categoryRepository.save(category);
    }

    public List<Category> fetchTopCategories(int limit) {
        // Books reference categories via @DBRef, stored as { $ref: 'category', $id: ObjectId }.
        // Counting with a find query on "categories.$id" is simpler and more reliable than an aggregation over DBRefs.
        List<Category> categories = categoryRepository.findByIsSeededTrue();
        for (Category category : categories) {
            Object id = ObjectId.isValid(category.getId()) ? new ObjectId(category.getId()) : category.getId();
            long count = mongoTemplate.count(new BasicQuery(new Document("categories.$id", id)), Book.class);
            category.setBooksCount((int) count);
        }
        return categories.stream()
                .sorted(Comparator.comparing(Category::getBooksCount).reversed())
                .limit(limit)
                .toList();
    }

    public void delete(String id) {
        categoryRepository.deleteById(id);
    }

}
