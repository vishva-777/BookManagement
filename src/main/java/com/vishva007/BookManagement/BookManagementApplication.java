package com.vishva007.BookManagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BookManagementApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(BookManagementApplication.class);
		app.addInitializers(new com.vishva007.BookManagement.config.SecretsManagerInitializer());
		app.run(args);
	}
}