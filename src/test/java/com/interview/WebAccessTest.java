package com.interview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
class WebAccessTest {

    @Autowired MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"/", "/index.html", "/styles.css", "/app.js", "/api.js"})
    void servesPublicWebsiteAssets(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @Test
    void preservesProtectedApiStatusesAndDoesNotOpenOtherStaticRequests() throws Exception {
        mvc.perform(get("/api/resumes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/sessions")).andExpect(status().isForbidden());
        mvc.perform(post("/api/sessions")).andExpect(status().isForbidden());
        mvc.perform(post("/api/answers")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/sessions/1/done")).andExpect(status().isForbidden());
        mvc.perform(get("/other.js")).andExpect(status().isForbidden());
        mvc.perform(get("/index.html/other")).andExpect(status().isForbidden());
        mvc.perform(post("/app.js")).andExpect(status().isForbidden());
    }
}
