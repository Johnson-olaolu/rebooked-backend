package com.personal.rebooked.category;

import com.personal.rebooked.category.dto.CreateCategoryDTO;
import com.personal.rebooked.category.dto.UpdateCategoryDTO;
import com.personal.rebooked.category.models.Category;
import com.personal.rebooked.utils.ResponseHandler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RequestMapping("/category")
@RestController
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("")
    public ResponseEntity<Object> getAllCategories() {
        List<Category> categories = categoryService.findAll();
        return ResponseHandler.generateResponse(categories , "Categories fetched successfully");
    }

    @PostMapping("")
    public ResponseEntity<Object> createCategory(@RequestBody @Valid CreateCategoryDTO createCategoryDTO) {
        Category category = categoryService.create(createCategoryDTO);
        return ResponseHandler.generateResponse(category , "Category created successfully");
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> getCategoryById(@PathVariable String id) {
        Category category = categoryService.findById(id);
        return ResponseHandler.generateResponse(category , "Category fetched successfully");
    }

    @GetMapping("/name")
    public ResponseEntity<Object> getCategoryByName(@RequestParam String name) {
        Category category = categoryService.findByName(name);
        return ResponseHandler.generateResponse(category , "Category fetched successfully");
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Object> updateCategory(@PathVariable String id, @Valid @RequestBody UpdateCategoryDTO updateCategoryDTO) {
        Category category = categoryService.update(id, updateCategoryDTO);
        return ResponseHandler.generateResponse(category , "Category updated successfully");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteCategory(@PathVariable String id) {
        categoryService.delete(id);
        return  ResponseHandler.generateResponse(null,  "Category deleted successfully");
    }
}
