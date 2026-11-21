package com.interview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    void preservesSignupAndLoginJsonContract() throws Exception {
        String credentials = """
                {"email":"auth-contract@example.com","password":"test-password"}
                """;
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials.replace("test-password", "wrong-password")))
                .andExpect(status().isUnauthorized());
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
