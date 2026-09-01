package com.vishva007.BookManagement.config;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Service
public class SqsPublisher {

    private final SqsClient sqsClient = SqsClient.builder().build();
    private final String queueUrl = "https://sqs.eu-north-1.amazonaws.com/902685117068/book-thumbnail-queue";

    public void sendBookAddedMessage(String bookTitle) {
        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody("New book added: " + bookTitle)
                .build();

        sqsClient.sendMessage(request);
        System.out.println("SQS message sent for book: " + bookTitle);
    }
}