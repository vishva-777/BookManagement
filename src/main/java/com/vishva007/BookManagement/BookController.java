package com.vishva007.BookManagement;

import com.vishva007.BookManagement.config.SqsPublisher;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/books")
public class BookController {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private SqsPublisher sqsPublisher;
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
    public Book create(@Valid @RequestBody Book book) {
        Book savedBook = bookRepository.save(book);
        sqsPublisher.sendBookAddedMessage(savedBook.getTitle());
        return savedBook;
    }

    @PutMapping("/{id}")
    public Book update(@PathVariable Long id, @Valid @RequestBody Book book) {
        Book existing = bookRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Book not found with id " + id));
        existing.setTitle(book.getTitle());
        existing.setDescription(book.getDescription());
        existing.setPrice(book.getPrice());
        existing.setAuthor(book.getAuthor());
        return bookRepository.save(existing);
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        bookRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Book not found with id " + id));
        bookRepository.deleteById(id);
        return "Book has been deleted";
    }

}