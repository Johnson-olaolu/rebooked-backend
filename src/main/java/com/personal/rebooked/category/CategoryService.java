package com.personal.rebooked.category;

import com.personal.rebooked.category.dto.CreateCategoryDTO;
import com.personal.rebooked.category.dto.UpdateCategoryDTO;
import com.personal.rebooked.category.models.Category;
import com.personal.rebooked.category.repositories.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RequiredArgsConstructor
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    public Category findById(String id) {
        return categoryRepository.findById(id).orElseThrow(() ->  new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found")
        );
    }

    public  Category findByName(String name){
        return  categoryRepository.findByName(name).orElseThrow(() ->  new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
    }

    public Category create(CreateCategoryDTO createCategoryDTO) {
        Category category = new Category();
        category.setName(createCategoryDTO.name());
        return categoryRepository.save(category);
    }

    public Category update(String id, UpdateCategoryDTO updateCategoryDTO) {
        Category category = findById(id);
        if(updateCategoryDTO.name() != null) {
            category.setName(updateCategoryDTO.name());
        }
        return categoryRepository.save(category);
    }

    public void delete(String id) {
        categoryRepository.deleteById(id);
    }

}
