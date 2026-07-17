package com.hrniux.underwriting.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class DemoConsoleStaticResourceTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void exposesTheDemoConsoleAtAStableTrailingSlashUrl() throws Exception {
        mvc.perform(get("/demo"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/demo/"));

        mvc.perform(get("/demo/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/demo/index.html"));
    }

    @Test
    void servesAnAccessibleChineseDemoShell() throws Exception {
        String html = mvc.perform(get("/demo/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/html")))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains(
                "中文智能核保演示台",
                "本页面全部业务数据均为虚构数据",
                "id=\"scenario-list\"",
                "id=\"run-evaluation\"",
                "aria-live=\"polite\"");
    }
}
