package com.vishva007.BookManagement;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/author")
public class AuthorController {

    @Autowired
    private AuthorRepository authorRepository;

    @GetMapping
    public List<Author> findAll() { return authorRepository.findAll(); }

    @GetMapping("/{id}")
    public Author findById(@PathVariable Long id) {
        return authorRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Author not found"));
    }

    @PostMapping
    public Author save(@Valid @RequestBody Author author) {
        return authorRepository.save(author);
    }

    @PutMapping("/{id}")
    public Author update(@PathVariable Long id, @Valid @RequestBody Author author) {
        Author existing = authorRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Author not found"));
        if(existing != null) {
            existing.setName(author.getName());
            return authorRepository.save(existing);
        }
        return null;
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        authorRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Author not found"));
        authorRepository.deleteById(id);
        return "Author with id " + id + " was deleted";
    }
}