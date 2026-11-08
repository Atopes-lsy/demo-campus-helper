package com.campus.helper.integration;

import com.campus.helper.cache.CacheKeys;
import com.campus.helper.common.BusinessException;
import com.campus.helper.entity.OrderDocument;
import com.campus.helper.security.LoginAttemptService;
import com.campus.helper.security.TokenVersionService;
import com.campus.helper.event.OrderEvent;
import com.campus.helper.event.KafkaConfig;
import com.campus.helper.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.*;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "outbox.publish-interval-ms=200",
                "outbox.batch-size=10",
                "dispatch.auto-enabled=false",
                "security.jwt.secret=test-integration-jwt-secret-at-least-32-bytes",
                "security.admin.username=admin",
                "security.admin.password=admin123",
                "payment.callback-secret=test-integration-payment-secret",
                "deepseek.api.key=sk-test-placeholder"
        })
class CampusHelperApplicationIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("campus_helper")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer(
            DockerImageName.parse("docker.1ms.run/confluentinc/cp-kafka:8.3.0")
                    .asCompatibleSubstituteFor("confluentinc/cp-kafka"));

    @Container
    static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(
            DockerImageName.parse("docker.1ms.run/elasticsearch:8.13.4")
                    .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.elasticsearch.uris", ELASTICSEARCH::getHttpHostAddress);
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ElasticsearchOperations elasticsearchOperations;

    @Autowired
    RedissonClient redissonClient;

    @Autowired
    LoginAttemptService loginAttemptService;

    @Autowired
    TokenVersionService tokenVersionService;

    @Autowired OrderService orderService;
    @Autowired KafkaTemplate<String, OrderEvent> kafka;
    @Autowired ObjectMapper objectMapper;

    /** Real service transactions and MySQL CAS, with a barrier releasing 16 callers together. */
    @Test
    void sixteenRunnersCompeteForOneOrderWithoutDuplicateSideEffects() throws Exception {
        String student = login("/api/auth/student/login",
                Map.of("studentNo", "20240101001", "password", "123456"));
        long orderId = createEvidenceOrder(student, "并发验收");
        int contenders = 16;
        List<Long> runners = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            String no = "R" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
            jdbc.update("INSERT INTO runner(runner_no,name,phone,id_card,status,rating,max_concurrent)"
                            + " VALUES(?,?,?,?,0,5.0,3)", no, "并发验收", "13800000000", "110101199001010011");
            runners.add(jdbc.queryForObject("SELECT id FROM runner WHERE runner_no=?", Long.class, no));
        }
        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CyclicBarrier barrier = new CyclicBarrier(contenders);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (long runnerId : runners) {
                results.add(pool.submit(() -> {
                    barrier.await(15, TimeUnit.SECONDS);
                    try {
                        orderService.updateStatus(orderId, 1, runnerId, "RUNNER");
                        return 200;
                    } catch (BusinessException conflict) {
                        // A caller that reads after the winner commits sees 1→1 (400);
                        // a caller that read 0 but loses the CAS receives 409.
                        assertThat(conflict.getCode()).isIn(400, 409);
                        return conflict.getCode();
                    }
                }));
            }
            int successes = 0;
            for (Future<Integer> result : results) {
                if (result.get(45, TimeUnit.SECONDS) == 200) successes++;
            }
            assertThat(successes).isEqualTo(1);
            Long owner = jdbc.queryForObject("SELECT runner_id FROM order_info WHERE id=?", Long.class, orderId);
            assertThat(runners).contains(owner);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_tracking WHERE order_id=?"
                    + " AND status_from=0 AND status_to=1", Integer.class, orderId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE aggregate_id=?"
                    + " AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.eventType'))='ORDER_ACCEPTED'",
                    Integer.class, orderId)).isEqualTo(1);
            long busy = runners.stream().filter(id -> Integer.valueOf(1).equals(jdbc.queryForObject(
                    "SELECT status FROM runner WHERE id=?", Integer.class, id))).count();
            assertThat(busy).isEqualTo(1);
            System.out.printf("EVIDENCE concurrency: callers=%d winners=%d claim-tracking=1 claim-outbox=1 busy-runners=%d%n",
                    contenders, successes, busy);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /** Pause only this test's Kafka container; restore it even if an assertion fails. */
    @Test
    void brokerOutageRecoversAndDuplicateDeliveryDoesNotDuplicateNotifications() throws Exception {
        String student = login("/api/auth/student/login",
                Map.of("studentNo", "20240101001", "password", "123456"));
        long orderId;
        KAFKA.getDockerClient().pauseContainerCmd(KAFKA.getContainerId()).exec();
        try {
            orderId = createEvidenceOrder(student, "故障恢复验收");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_info WHERE id=?",
                    Integer.class, orderId)).isEqualTo(1);
            Awaitility.await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> {
                assertThat(jdbc.queryForObject("SELECT status FROM event_outbox WHERE aggregate_id=?",
                        String.class, orderId)).isEqualTo("FAILED");
                assertThat(jdbc.queryForObject("SELECT retry_count FROM event_outbox WHERE aggregate_id=?",
                        Integer.class, orderId)).isGreaterThanOrEqualTo(1);
            });
            System.out.println("EVIDENCE outage: order-committed=1 outbox=FAILED retry>=1");
        } finally {
            KAFKA.getDockerClient().unpauseContainerCmd(KAFKA.getContainerId()).exec();
        }
        String eventId = jdbc.queryForObject("SELECT event_id FROM event_outbox WHERE aggregate_id=?",
                String.class, orderId);
        Awaitility.await().atMost(Duration.ofSeconds(90)).untilAsserted(() -> {
            assertThat(jdbc.queryForObject("SELECT status FROM event_outbox WHERE event_id=?",
                    String.class, eventId)).isEqualTo("SENT");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM processed_event WHERE event_id=?",
                    Integer.class, eventId)).isEqualTo(4);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE related_order_id=?",
                    Integer.class, orderId)).isEqualTo(1);
            assertThat(elasticsearchOperations.get(String.valueOf(orderId), OrderDocument.class)).isNotNull();
        });
        OrderEvent original = objectMapper.readValue(jdbc.queryForObject(
                "SELECT payload FROM event_outbox WHERE event_id=?", String.class, eventId), OrderEvent.class);
        for (int i = 0; i < 3; i++) {
            kafka.send(KafkaConfig.ORDER_EVENTS_TOPIC, original.getOrderNo(), original).get(20, TimeUnit.SECONDS);
        }
        // Same partition, new ID: its notification is a processing fence after the duplicates.
        OrderEvent fence = objectMapper.readValue(objectMapper.writeValueAsString(original), OrderEvent.class);
        fence.setEventId(UUID.randomUUID().toString());
        kafka.send(KafkaConfig.ORDER_EVENTS_TOPIC, original.getOrderNo(), fence).get(20, TimeUnit.SECONDS);
        Awaitility.await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM processed_event WHERE event_id=?"
                    + " AND consumer_name='notification'", Integer.class, fence.getEventId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE related_order_id=?",
                    Integer.class, orderId)).isEqualTo(2); // original + fence, never duplicates
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM processed_event WHERE event_id=?"
                    + " AND consumer_name='notification'", Integer.class, eventId)).isEqualTo(1);
        });
        System.out.println("EVIDENCE recovery: outbox=SENT consumers=4 es-document=present duplicate-replays=3 notifications=2(original+fence)");
    }

    private long createEvidenceOrder(String studentToken, String label) {
        ResponseEntity<JsonNode> response = rest.exchange(url("/api/orders"), HttpMethod.POST,
                jsonEntity(Map.of("orderType", 2, "pickupLocation", label,
                        "deliveryLocation", "验收宿舍", "packages", List.of(Map.of("size", "small")),
                        "tip", 0), studentToken), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        long id = response.getBody().path("data").path("id").asLong();
        assertThat(id).isPositive();
        return id;
    }

    /**
     * The index is written to through the low-level client, which would leave Elasticsearch
     * to infer the mapping — and dynamic mapping turns "lat,lon" into text rather than
     * geo_point, breaking the geo queries the search service issues.
     */
    @Test
    void createsOrderIndexWithTheDeclaredMapping() {
        Map<String, Object> mapping = elasticsearchOperations.indexOps(OrderDocument.class).getMapping();
        Object properties = mapping.get("properties");
        assertThat(properties).isInstanceOf(Map.class);
        Map<?, ?> fields = (Map<?, ?>) properties;

        assertThat(mappedType(fields, "pickupGeoPoint")).isEqualTo("geo_point");
        assertThat(mappedType(fields, "deliveryGeoPoint")).isEqualTo("geo_point");
        assertThat(mappedType(fields, "createdAt")).isEqualTo("date");
        assertThat(mappedType(fields, "updatedAt")).isEqualTo("date");
    }

    private Object mappedType(Map<?, ?> properties, String field) {
        Object definition = properties.get(field);
        assertThat(definition).as("索引映射缺少字段 %s", field).isInstanceOf(Map.class);
        return ((Map<?, ?>) definition).get("type");
    }

    @Test
    void flywaySecurityOutboxKafkaAndElasticsearchWorkTogether() {
        ResponseEntity<JsonNode> anonymous = rest.getForEntity(url("/api/payments"), JsonNode.class);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String adminToken = login("/api/auth/admin/login",
                Map.of("username", "admin", "password", "admin123"));
        String studentToken = login("/api/auth/student/login",
                Map.of("studentNo", "20240101001", "password", "123456"));

        // Bump this when adding a migration — it is what proves the migrations ran.
        Integer flywayVersion = jdbc.queryForObject(
                "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1",
                Integer.class);
        assertThat(flywayVersion).isEqualTo(3);

        Map<String, Object> order = Map.of(
                "orderType", 2,
                "pickupLocation", "测试取件点",
                "deliveryLocation", "测试宿舍",
                "packages", java.util.List.of(Map.of("size", "small")),
                "tip", 1
        );
        ResponseEntity<JsonNode> created = rest.exchange(url("/api/orders"), HttpMethod.POST,
                jsonEntity(order, studentToken), JsonNode.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = created.getBody().path("data").path("id").asLong();
        assertThat(orderId).isPositive();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_outbox WHERE aggregate_id=?", Integer.class, orderId))
                .isEqualTo(1);

        assertPaginationIsApplied(studentToken);

        Awaitility.await().atMost(Duration.ofSeconds(40)).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    assertThat(jdbc.queryForObject(
                            "SELECT status FROM event_outbox WHERE aggregate_id=?", String.class, orderId))
                            .isEqualTo("SENT");
                    assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM processed_event WHERE event_id=(SELECT event_id FROM event_outbox WHERE aggregate_id=?)",
                            Integer.class, orderId)).isEqualTo(4);

                    ResponseEntity<JsonNode> search = rest.exchange(url("/api/search/orders"),
                            HttpMethod.GET, bearerEntity(adminToken), JsonNode.class);
                    assertThat(search.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(search.getBody().path("data").path("total").asLong()).isGreaterThanOrEqualTo(1);
                });
    }

    /**
     * MyBatis-Plus only emits LIMIT and the COUNT query when the pagination interceptor is
     * registered; without it {@code selectPage} runs the statement unwrapped and {@code total}
     * stays 0. These assertions fail if that bean is ever removed.
     */
    private void assertPaginationIsApplied(String studentToken) {
        ResponseEntity<JsonNode> firstPage = rest.exchange(url("/api/orders?page=1&size=1"),
                HttpMethod.GET, bearerEntity(studentToken), JsonNode.class);
        assertThat(firstPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = firstPage.getBody().path("data");

        assertThat(data.path("records")).as("size=1 应只返回 1 条记录").hasSize(1);
        assertThat(data.path("total").asLong()).as("COUNT 查询必须被执行").isPositive();
        assertThat(data.path("size").asLong()).isEqualTo(1);
    }

    /**
     * A pricing-rule write must drop the cached rule set, and only after the write commits —
     * otherwise a concurrent dispatch could re-cache the values it just replaced.
     */
    @Test
    void updatingAPricingRuleEvictsTheCachedRules() {
        String adminToken = login("/api/auth/admin/login",
                Map.of("username", "admin", "password", "admin123"));

        Long ruleId = jdbc.queryForObject(
                "SELECT id FROM pricing_rule ORDER BY id LIMIT 1", Long.class);

        // Stand in for a warm cache entry.
        redissonClient.getBucket(CacheKeys.ACTIVE_PRICING_RULES).set("[]");
        assertThat(redissonClient.getBucket(CacheKeys.ACTIVE_PRICING_RULES).isExists()).isTrue();

        ResponseEntity<JsonNode> updated = rest.exchange(
                url("/api/ai/pricing/" + ruleId), HttpMethod.PUT,
                jsonEntity(Map.of("priceFactor", 0.35, "aiReasoning", "integration test"), adminToken),
                JsonNode.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(redissonClient.getBucket(CacheKeys.ACTIVE_PRICING_RULES).isExists())
                .as("写入提交后必须失效派单权重缓存")
                .isFalse();
    }

    /** The pricing write lives under /api/ai/**, which is ADMIN-only. */
    @Test
    void pricingRuleWritesAreRejectedForNonAdmins() {
        String studentToken = login("/api/auth/student/login",
                Map.of("studentNo", "20240101001", "password", "123456"));
        Long ruleId = jdbc.queryForObject(
                "SELECT id FROM pricing_rule ORDER BY id LIMIT 1", Long.class);

        ResponseEntity<JsonNode> forbidden = rest.exchange(
                url("/api/ai/pricing/" + ruleId), HttpMethod.PUT,
                jsonEntity(Map.of("priceFactor", 0.9), studentToken), JsonNode.class);

        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * A caller's mistake must not be reported as a server fault. Before the exception
     * handler discriminated between them, all of these returned 500.
     */
    @Test
    void clientErrorsKeepTheirOwnStatusCodes() {
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        HttpHeaders plain = new HttpHeaders();
        plain.setContentType(MediaType.TEXT_PLAIN);

        assertThat(rest.postForEntity(url("/api/auth/student/login"),
                new HttpEntity<>("{\"studentNo\": ", json), JsonNode.class).getStatusCode())
                .as("请求体 JSON 格式错误应为 400").isEqualTo(HttpStatus.BAD_REQUEST);

        String adminToken = login("/api/auth/admin/login",
                Map.of("username", "admin", "password", "admin123"));
        assertThat(rest.exchange(url("/api/bi/dashboard?days=abc"), HttpMethod.GET,
                bearerEntity(adminToken), JsonNode.class).getStatusCode())
                .as("参数类型无法解析应为 400").isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(rest.exchange(url("/api/bi/overview"), HttpMethod.DELETE,
                bearerEntity(adminToken), JsonNode.class).getStatusCode())
                .as("方法不支持应为 405").isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);

        assertThat(rest.exchange(url("/api/does-not-exist"), HttpMethod.GET,
                bearerEntity(adminToken), JsonNode.class).getStatusCode())
                .as("路径不存在应为 404").isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(rest.postForEntity(url("/api/auth/student/login"),
                new HttpEntity<>("hello", plain), JsonNode.class).getStatusCode())
                .as("Content-Type 不支持应为 415").isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    /**
     * A failure counter must never exist without a TTL.
     *
     * <p>Recording a failure used to be INCR followed by a separate EXPIRE, applied only when
     * the count was 1. Crashing between the two left a counter with no expiry, which locks the
     * account permanently once it reaches the threshold. The script now repairs a missing TTL
     * on every increment, so this state heals instead of persisting.
     */
    @Test
    void failureCounterAlwaysEndsUpWithATtl() {
        String key = "auth:fail:STUDENT:20240101001";
        RAtomicLong counter = redissonClient.getAtomicLong(key);

        // The aftermath of the crash: an existing count with no expiry.
        counter.set(3);
        assertThat(counter.remainTimeToLive())
                .as("前置条件：该键此刻没有 TTL").isEqualTo(-1);

        loginAttemptService.recordFailure("STUDENT", "20240101001");

        assertThat(counter.remainTimeToLive())
                .as("记录失败后必须带上 TTL，否则账号会被永久锁定")
                .isPositive();
        assertThat(counter.get())
                .as("计数应加在同一把键上").isEqualTo(4);

        counter.delete();
    }

    /** The threshold itself: the sixth attempt is refused even with the right password. */
    @Test
    void repeatedFailuresLockTheAccount() {
        String key = "auth:fail:STUDENT:20240101002";
        RAtomicLong counter = redissonClient.getAtomicLong(key);
        counter.delete();

        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure("STUDENT", "20240101002");
        }

        assertThatThrownBy(() -> loginAttemptService.assertNotLocked("STUDENT", "20240101002"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录失败次数过多");

        // A successful login clears the counter.
        loginAttemptService.reset("STUDENT", "20240101002");
        assertThat(counter.isExists()).isFalse();
    }

    /**
     * A JWT cannot be recalled once issued, so the server keeps a per-account version and the
     * token carries the value it was issued at. Logging out bumps that version, which retires
     * every token the account holds — including the one making the request.
     */
    @Test
    void logoutRevokesTheTokenThatMadeTheRequest() {
        String token = login("/api/auth/student/login",
                Map.of("studentNo", "20240101003", "password", "123456"));

        assertThat(authorized(token)).as("登出前 token 可用").isEqualTo(HttpStatus.OK);

        assertThat(rest.exchange(url("/api/auth/logout"), HttpMethod.POST,
                bearerEntity(token), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(authorized(token)).as("登出后同一个 token 必须失效")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** Disabling an account has to end its sessions, not merely block the next login. */
    @Test
    void disablingAStudentEndsTheirSessions() {
        String token = login("/api/auth/student/login",
                Map.of("studentNo", "20240101004", "password", "123456"));
        String adminToken = login("/api/auth/admin/login",
                Map.of("username", "admin", "password", "admin123"));
        Long studentId = jdbc.queryForObject(
                "SELECT id FROM student WHERE student_no = '20240101004'", Long.class);

        assertThat(rest.exchange(url("/api/students/" + studentId), HttpMethod.PUT,
                jsonEntity(Map.of("status", 0), adminToken), JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(authorized(token)).as("禁用账号后其既有 token 必须立即失效")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** Revoking someone else's sessions is an administrative action. */
    @Test
    void onlyAdminsCanRevokeARunnersSessions() {
        String studentToken = login("/api/auth/student/login",
                Map.of("studentNo", "20240101002", "password", "123456"));
        String adminToken = login("/api/auth/admin/login",
                Map.of("username", "admin", "password", "admin123"));

        assertThat(rest.exchange(url("/api/runners/1/revoke-tokens"), HttpMethod.POST,
                bearerEntity(studentToken), JsonNode.class).getStatusCode())
                .as("非管理员不得失效他人会话").isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(rest.exchange(url("/api/runners/1/revoke-tokens"), HttpMethod.POST,
                bearerEntity(adminToken), JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    /**
     * bump() and currentVersion() must agree on both the key and the value encoding.
     *
     * <p>They did not at first: bump used RAtomicLong (a Lua INCR, so a plain integer in
     * Redis) while the read used RBucket, whose default Kryo codec cannot decode a plain
     * integer. The read threw, the resolver reported "unknown", and the revocation check was
     * silently skipped — logout appeared to succeed while the token kept working.
     */
    @Test
    void bumpedVersionIsReadBackByTheResolver() {
        String key = "auth:ver:DIAG:999";
        redissonClient.getAtomicLong(key).delete();
        assertThat(tokenVersionService.currentVersion("DIAG", 999L))
                .as("从未递增过的账号版本为 0").isEqualTo(0L);

        tokenVersionService.bump("DIAG", 999L);
        tokenVersionService.bump("DIAG", 999L);

        assertThat(tokenVersionService.currentVersion("DIAG", 999L))
                .as("读写必须用同一套编码，否则版本校验会静默失效")
                .isEqualTo(2L);

        redissonClient.getAtomicLong(key).delete();
    }

    /** Any authenticated endpoint, used to tell whether a token is still accepted. */
    private HttpStatusCode authorized(String token) {
        return rest.exchange(url("/api/orders"), HttpMethod.GET, bearerEntity(token), JsonNode.class)
                .getStatusCode();
    }

    private String login(String path, Map<String, String> credentials) {
        ResponseEntity<JsonNode> response = rest.postForEntity(
                url(path), jsonEntity(credentials, null), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = response.getBody().path("data").path("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private HttpEntity<?> jsonEntity(Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Void> bearerEntity(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
