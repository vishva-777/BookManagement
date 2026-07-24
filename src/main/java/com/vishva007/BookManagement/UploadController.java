package com.vishva007.BookManagement;

import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import java.time.Duration;
import java.util.UUID;
import java.util.Map;

@RestController
@RequestMapping("/books")
public class UploadController {

    private final S3Presigner s3Presigner;
    private static final String BUCKET = "vishva-bookmanagement-covers";
    private static final String CLOUDFRONT_DOMAIN = "https://d3q1c59sdujuhr.cloudfront.net";

    public UploadController(S3Presigner s3Presigner) {
        this.s3Presigner = s3Presigner;
    }

    @GetMapping("/upload-url")
    public Map<String, String> getUploadUrl(@RequestParam String filename) {
        String key = UUID.randomUUID() + "-" + filename;

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(BUCKET)
                .key(key)
                .contentType("image/jpeg")
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        String uploadUrl = presignedRequest.url().toString();
        String finalImageUrl = CLOUDFRONT_DOMAIN + "/" + key;

        return Map.of(
                "uploadUrl", uploadUrl,
                "finalImageUrl", finalImageUrl
        );
    }
}