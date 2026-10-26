package com.zjgsu.njb.pet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * pet-service API 层测试：建档 / 查询 / 404（MockMvc，不启动真实端口）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PetApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createThenGetPet() throws Exception {
        String body = """
                {"name":"豆豆","species":"犬","breed":"金毛","ownerName":"倪先生",
                 "ownerPhone":"13800000001","birthday":"2021-05-20",
                 "weightKg":28.50,"allergyHistory":"青霉素过敏"}
                """;

        String location = mockMvc.perform(post("/api/pets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("豆豆"))
                .andExpect(jsonPath("$.ownerName").value("倪先生"))
                .andReturn().getResponse().getHeader("Location");

        assertThat(location).isNotBlank();

        String id = location.substring(location.lastIndexOf('/') + 1);
        mockMvc.perform(get("/api/pets/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.species").value("犬"));
    }

    @Test
    void getMissingPetReturns404() throws Exception {
        mockMvc.perform(get("/api/pets/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void createWithBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/pets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"species\":\"犬\",\"ownerName\":\"倪先生\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }
}
