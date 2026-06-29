package com.vishva007.BookManagement;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/books")
public class BookController {

    @Autowired
    private BookRepository bookRepository;

    @GetMapping
    public List<Book> findAll() {
        return bookRepository.findAll();
    }

    @GetMapping("/{id}")
    public Book findById(@PathVariable Long id) {
        return bookRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Book not found with id " + id));
    }

    @PostMapping
    public Book create(@RequestBody Book book) {
        return bookRepository.save(book);
    }

    @PutMapping("/{id}")
    public Book update(@PathVariable Long id, @RequestBody Book book) {
        Book existing = bookRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Book not found with id " + id));
        if(existing != null) {
            existing.setTitle(book.getTitle());
            existing.setPrice(book.getPrice());
            return bookRepository.save(existing);
        }
        return null;
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        bookRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Book not found with id " + id));
        bookRepository.deleteById(id);
        return "Book has been deleted";
    }

}