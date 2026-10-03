package com.plantride.support;


import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Thin MockMvc wrapper: JSON in, JSON out, asserting the expected HTTP status. */
public class ApiClient {

    private final MockMvc mvc;
    private final ObjectMapper om;

    public ApiClient(MockMvc mvc, ObjectMapper om) {
        this.mvc = mvc;
        this.om = om;
    }

    public String login(String loginId, String password) throws Exception {
        return post(null, "/api/auth/login", java.util.Map.of("loginId", loginId, "password", password), 200)
                .get("token").asText();
    }

    public JsonNode post(String token, String url, Object body, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder req = MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "{}" : om.writeValueAsString(body));
        return exec(token, req, expectedStatus);
    }

    public JsonNode get(String token, String url, int expectedStatus) throws Exception {
        return exec(token, MockMvcRequestBuilders.get(url), expectedStatus);
    }

    public JsonNode postWithHeader(String url, Object body, String header, String value, int expectedStatus)
            throws Exception {
        MockHttpServletRequestBuilder req = MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(body)).header(header, value);
        return exec(null, req, expectedStatus);
    }

    private JsonNode exec(String token, MockHttpServletRequestBuilder req, int expectedStatus) throws Exception {
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mvc.perform(req).andReturn();
        int status = result.getResponse().getStatus();
        String content = result.getResponse().getContentAsString();
        if (status != expectedStatus) {
            throw new AssertionError("Expected HTTP " + expectedStatus + " but got " + status + ": " + content);
        }
        return content.isBlank() ? om.nullNode() : om.readTree(content);
    }
}
