package io.github.wraithyy.javacite.example;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GreetingController.class)
class GreetingControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GreetingService service;

    @Test
    void returnsGreeting() throws Exception {
        when(service.greet("Ann", "en")).thenReturn("Hello, Ann!");

        mvc.perform(get("/greeting").param("name", "Ann"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, Ann!"));
    }
}
