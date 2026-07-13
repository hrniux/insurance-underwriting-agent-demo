package com.hrniux.underwriting.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
class ManagementApiIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void ingestsListsAndSearchesKnowledge() throws Exception {
        String documentId = "API-" + UUID.randomUUID();
        mvc.perform(post("/api/v1/knowledge/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"%s","title":"面试演示条款","type":"PRODUCT_CLAUSE",
                                 "productCode":"PROPERTY","content":"暴雨红色风险应转人工复核。","metadata":{"source":"api-test"}}
                                """.formatted(documentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value(documentId))
                .andExpect(jsonPath("$.chunkCount").value(1));

        mvc.perform(get("/api/v1/knowledge/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(documentId)).exists());

        mvc.perform(post("/api/v1/knowledge/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"暴雨人工复核\",\"topK\":4,\"productCode\":\"PROPERTY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].chunk.content").isNotEmpty());
    }

    @Test
    void listsCreatesActivatesAndPreviewsPromptVersions() throws Exception {
        String code = "api-template-" + UUID.randomUUID();

        mvc.perform(get("/api/v1/prompts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'underwriting-analysis')]").exists());

        String created = mvc.perform(post("/api/v1/prompts/{code}/versions", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"问题：{{question}}\",\"requiredVariables\":[\"question\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();
        int version = JsonPath.read(created, "$.version");

        mvc.perform(post("/api/v1/prompts/{code}/versions/{version}/activate", code, version))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mvc.perform(post("/api/v1/prompts/{code}/preview", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variables\":{\"question\":\"是否承保\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rendered").value("问题：是否承保"));
    }

    @Test
    void listsAndInvokesTheSharedToolRegistry() throws Exception {
        mvc.perform(get("/api/v1/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@ == 'GET_POLICY')]").exists())
                .andExpect(jsonPath("$[?(@ == 'VALIDATE_RULES')]").exists());

        mvc.perform(post("/api/v1/tools/GET_POLICY/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyNo\":\"P-1001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.policyNo").value("P-1001"))
                .andExpect(jsonPath("$.trace.status").value("SUCCESS"));
    }
}
