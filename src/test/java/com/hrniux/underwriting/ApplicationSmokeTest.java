package com.hrniux.underwriting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.hrniux.underwriting.agent.EvaluationRepository;
import com.hrniux.underwriting.agent.InMemoryEvaluationRepository;
import com.hrniux.underwriting.review.HumanReviewRepository;
import com.hrniux.underwriting.review.InMemoryHumanReviewRepository;
import com.hrniux.underwriting.session.InMemorySessionRepository;
import com.hrniux.underwriting.session.SessionRepository;

@SpringBootTest
class ApplicationSmokeTest {

    @Autowired
    private SessionRepository sessions;

    @Autowired
    private EvaluationRepository evaluations;

    @Autowired
    private HumanReviewRepository reviews;

    @Test
    void startsWithoutAnApiKey() {
        assertThat(sessions).isInstanceOf(InMemorySessionRepository.class);
        assertThat(evaluations).isInstanceOf(InMemoryEvaluationRepository.class);
        assertThat(reviews).isInstanceOf(InMemoryHumanReviewRepository.class);
    }
}
