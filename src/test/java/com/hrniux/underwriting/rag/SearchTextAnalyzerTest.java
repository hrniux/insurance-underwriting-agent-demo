package com.hrniux.underwriting.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchTextAnalyzerTest {

    @Test
    void createsChineseUnigramsAndBigramsAndNormalizesIdentifiers() {
        var tokens = SearchTextAnalyzer.tokenize("暴雨条款 CLAUSE-RAIN-900");

        assertThat(tokens).contains("暴", "雨", "条", "款", "暴雨", "雨条", "条款",
                "clause", "rain", "900");
    }

    @Test
    void returnsNoTokensForBlankText() {
        assertThat(SearchTextAnalyzer.tokenize("  ")).isEmpty();
    }
}
