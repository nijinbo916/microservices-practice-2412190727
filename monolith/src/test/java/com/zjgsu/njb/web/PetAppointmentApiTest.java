package com.zjgsu.njb.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * REST API 集成测试：随机端口启动完整应用，用真实 HTTP 请求验证接口。
 *
 * <p>覆盖正常响应与错误响应两类场景：</p>
 * <ul>
 *   <li>POST /api/pets        201 创建成功 / 400 参数非法</li>
 *   <li>GET  /api/pets/{id}   200 查询成功 / 404 不存在</li>
 *   <li>POST /api/appointments 201 创建成功 / 409 号源冲突</li>
 *   <li>POST /api/appointments/{id}/cancel 200 取消成功</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("REST API 集成验证")
class PetAppointmentApiTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final Pattern ID_PATTERN_TEMPLATE = Pattern.compile("\"id\":(\\d+)");

    // ---------------------------------------------------------------- 工具方法

    private HttpResponse<String> send(String method, String path, String jsonBody) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json");
        if (jsonBody == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(jsonBody, java.nio.charset.StandardCharsets.UTF_8));
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private long firstIdOf(String json) {
        Matcher matcher = ID_PATTERN_TEMPLATE.matcher(json);
        assertThat(matcher.find()).as("响应中应包含 id 字段：%s", json).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private long createPet(String name) throws Exception {
        String body = """
                {"name":"%s","species":"犬","breed":"金毛","ownerName":"倪先生",
                 "ownerPhone":"13800000001","birthday":"2021-05-20","weightKg":28.50,
                 "allergyHistory":"青霉素过敏"}
                """.formatted(name);
        HttpResponse<String> response = send("POST", "/api/pets", body);
        assertThat(response.statusCode()).isEqualTo(201);
        return firstIdOf(response.body());
    }

    private long firstDoctorId() throws Exception {
        HttpResponse<String> response = send("GET", "/api/doctors", null);
        assertThat(response.statusCode()).isEqualTo(200);
        return firstIdOf(response.body());
    }

    private String appointmentJson(long petId, long doctorId, LocalDateTime start, int minutes, String reason) {
        return """
                {"petId":%d,"doctorId":%d,"slotStart":"%s","slotEnd":"%s","reason":"%s"}
                """.formatted(petId, doctorId, start, start.plusMinutes(minutes), reason);
    }

    // ---------------------------------------------------------------- 测试用例

    @Test
    @DisplayName("POST /api/pets 返回 201 并可按 id 查回；不存在的 id 返回 404")
    void createAndQueryPet() throws Exception {
        long petId = createPet("接口测试犬-" + System.nanoTime());

        HttpResponse<String> detail = send("GET", "/api/pets/" + petId, null);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).contains("\"id\":" + petId).contains("倪先生");

        HttpResponse<String> missing = send("GET", "/api/pets/99999999", null);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(missing.body()).contains("NOT_FOUND");
    }

    @Test
    @DisplayName("POST /api/pets 参数非法返回 400")
    void createPetWithBlankNameReturns400() throws Exception {
        String invalid = """
                {"name":"","species":"犬","ownerName":"倪先生"}
                """;
        HttpResponse<String> response = send("POST", "/api/pets", invalid);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("VALIDATION_FAILED").contains("宠物昵称不能为空");
    }

    @Test
    @DisplayName("GET /api/pets 支持按主人姓名过滤")
    void listPetsFilterByOwner() throws Exception {
        String owner = "过滤测试主人" + System.nanoTime();
        String body = """
                {"name":"过滤犬","species":"犬","ownerName":"%s"}
                """.formatted(owner);
        assertThat(send("POST", "/api/pets", body).statusCode()).isEqualTo(201);

        HttpResponse<String> response = send("GET", "/api/pets?ownerName=" + owner, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("过滤犬").contains(owner);
    }

    @Test
    @DisplayName("POST /api/appointments 创建成功返回 201，且可按宠物查回")
    void createAppointmentAndQueryByPet() throws Exception {
        long petId = createPet("预约犬-" + System.nanoTime());
        long doctorId = firstDoctorId();
        LocalDateTime start = LocalDate.now().plusDays(7).atTime(10, 0);

        HttpResponse<String> created = send("POST", "/api/appointments",
                appointmentJson(petId, doctorId, start, 30, "食欲不振"));
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("\"status\":\"BOOKED\"");

        HttpResponse<String> byPet = send("GET", "/api/appointments?petId=" + petId, null);
        assertThat(byPet.statusCode()).isEqualTo(200);
        assertThat(byPet.body()).contains("预约犬-").contains("食欲不振");
    }

    @Test
    @DisplayName("同一医生同一时段重复预约返回 409（业务规则）")
    void overlappingAppointmentReturns409() throws Exception {
        long petId = createPet("冲突犬-" + System.nanoTime());
        long doctorId = firstDoctorId();
        LocalDateTime start = LocalDate.now().plusDays(8).atTime(11, 0);

        HttpResponse<String> first = send("POST", "/api/appointments",
                appointmentJson(petId, doctorId, start, 30, "第一次"));
        assertThat(first.statusCode()).isEqualTo(201);

        HttpResponse<String> second = send("POST", "/api/appointments",
                appointmentJson(petId, doctorId, start.plusMinutes(10), 30, "冲突抢占"));
        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(second.body()).contains("BUSINESS_RULE_VIOLATION").contains("号源冲突");
    }

    @Test
    @DisplayName("取消预约返回 200，重复取消返回 409")
    void cancelAppointment() throws Exception {
        long petId = createPet("取消犬-" + System.nanoTime());
        long doctorId = firstDoctorId();
        LocalDateTime start = LocalDate.now().plusDays(9).atTime(15, 0);

        HttpResponse<String> created = send("POST", "/api/appointments",
                appointmentJson(petId, doctorId, start, 30, "体检"));
        assertThat(created.statusCode()).isEqualTo(201);
        long appointmentId = firstIdOf(created.body());

        HttpResponse<String> canceled = send("POST", "/api/appointments/" + appointmentId + "/cancel", null);
        assertThat(canceled.statusCode()).isEqualTo(200);
        assertThat(canceled.body()).contains("\"status\":\"CANCELED\"");

        HttpResponse<String> again = send("POST", "/api/appointments/" + appointmentId + "/cancel", null);
        assertThat(again.statusCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("预约时间非未来时间返回 400")
    void appointmentInThePastReturns400() throws Exception {
        long petId = createPet("过去犬-" + System.nanoTime());
        long doctorId = firstDoctorId();
        LocalDateTime past = LocalDateTime.now().minusDays(1);

        HttpResponse<String> response = send("POST", "/api/appointments",
                appointmentJson(petId, doctorId, past, 30, "过去时间"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("预约开始时间必须晚于当前时间");
    }
}
