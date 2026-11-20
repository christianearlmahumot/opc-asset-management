package edu.citu.opc.fams.reference;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<CategoryView> list() {
        return service.list().stream().map(c -> new CategoryView(c.getId(), c.getName())).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryView create(@Valid @RequestBody CategoryForm f) {
        Category c = service.create(f.name());
        return new CategoryView(c.getId(), c.getName());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryView rename(@PathVariable Long id, @Valid @RequestBody CategoryForm f) {
        Category c = service.rename(id, f.name());
        return new CategoryView(c.getId(), c.getName());
    }

    public record CategoryView(Long id, String name) {
    }

    public record CategoryForm(@NotBlank(message = "Enter the category name.") @Size(max = 60) String name) {
    }
}
