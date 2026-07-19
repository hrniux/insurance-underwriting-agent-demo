package com.hrniux.underwriting.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hrniux.underwriting.api.ApiDtos.HumanReviewApiRequest;
import com.hrniux.underwriting.review.HumanReview;
import com.hrniux.underwriting.review.HumanReviewService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/underwriting")
public class HumanReviewController {

    private final HumanReviewService reviews;

    public HumanReviewController(HumanReviewService reviews) {
        this.reviews = reviews;
    }

    @PostMapping("/evaluations/{evaluationId}/review")
    public ResponseEntity<HumanReview> submit(
            @PathVariable String evaluationId,
            @Valid @RequestBody HumanReviewApiRequest request) {
        HumanReview review = reviews.submit(evaluationId, request.toDomain());
        URI location = URI.create("/api/v1/underwriting/evaluations/" + evaluationId + "/review");
        return ResponseEntity.created(location).body(review);
    }

    @GetMapping("/evaluations/{evaluationId}/review")
    public HumanReview get(@PathVariable String evaluationId) {
        return reviews.get(evaluationId);
    }

    @GetMapping("/reviews")
    public List<HumanReview> list() {
        return reviews.list();
    }
}
