package com.vishva007.BookManagement.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

public class SecretsManagerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        try {
            SecretsManagerClient client = SecretsManagerClient.builder()
                    .region(Region.EU_NORTH_1)
                    .build();

            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId("bookmanagement/prod/db-password")
                    .build();

            GetSecretValueResponse response = client.getSecretValue(request);
            String secretJson = response.secretString();

            ObjectMapper mapper = new ObjectMapper();
            Map<String, String> secretMap = mapper.readValue(secretJson, Map.class);

            System.setProperty("DB_PASSWORD", String.valueOf(secretMap.get("password")));
            System.setProperty("DB_USERNAME", String.valueOf(secretMap.get("username")));
            System.setProperty("DB_ADMIN_PASSWORD", String.valueOf(secretMap.get("DB_ADMIN_PASSWORD")));
            System.setProperty("JWT_SECRET", String.valueOf(secretMap.get("JWT_SECRET")));

            String host = String.valueOf(secretMap.get("host"));
            String port = String.valueOf(secretMap.get("port"));
            String dbname = String.valueOf(secretMap.get("dbname"));
            String dbUrl = "jdbc:mysql://" + host + ":" + port + "/" + dbname;
            System.setProperty("DB_URL", dbUrl);

            System.out.println("Secrets Manager: DB credentials loaded successfully.");
        } catch (Exception e) {
            System.err.println("Failed to load secrets from Secrets Manager: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}