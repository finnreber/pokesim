package com.pokesim.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenApiDocsIntegrationTest extends IntegrationTestBase {

    @Test @DisplayName("the OpenAPI document is public and describes the API")
    void docsArePublic() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value(startsWith("3.")))
                .andExpect(jsonPath("$.info.title").value("Pokesim API"))
                .andExpect(jsonPath("$.paths['/api/v1/battles/{id}/actions']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/simulate']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/species/{dexNumber}']").exists());
    }

    @Test @DisplayName("the Authorize button is wired up: bearer scheme defined, protected endpoints marked, public ones not")
    void securityIsDocumented() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/teams'].post.security").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/battles'].post.security").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/species/{dexNumber}'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/simulate'].post.security").doesNotExist());
    }

    @Test @DisplayName("the injected login principal is hidden from the docs, so clients are not asked for a 'jwt' parameter")
    void principalIsHidden() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(content().string(not(containsString("\"name\":\"jwt\""))));
    }
}
