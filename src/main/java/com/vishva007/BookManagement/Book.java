package com.vishva007.BookManagement;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.*;

@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Description is required")
    private String description;

    @Min(value = 1, message = "Price must be at least 1")
    private Integer price;

    @ManyToOne
    @JoinColumn(name = "author_id")
    @JsonBackReference
    private Author author;

    public Author getAuthor() { return author; }
    public void setAuthor(Author author) { this.author = author; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }


    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }


    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getPrice() { return price; }
    public void setPrice(Integer price) { this.price = price; }
}
