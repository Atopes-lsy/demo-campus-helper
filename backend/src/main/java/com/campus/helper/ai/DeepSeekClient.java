package com.campus.helper.ai;

import com.jayway.jsonpath.JsonPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class DeepSeekClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

    private final RestClient restClient;

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Value("${deepseek.api.url:https://api.deepseek.com/v1}")
    private String apiUrl;

    public DeepSeekClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    public String chat(String systemPrompt, String userMessage, double temperature) {
        Map<String, Object> request = Map.of(
            "model", "deepseek-chat",
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
            ),
            "temperature", temperature,
            "max_tokens", 1024,
            "stream", false
        );

        try {
            String response = restClient.post()
                .uri(apiUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(request)
                .retrieve()
                .body(String.class);

            return JsonPath.parse(response).read("$.choices[0].message.content");
        } catch (Exception e) {
            log.error("DeepSeek API call failed: {}", e.getMessage());
            return "AI 服务暂时不可用，请稍后再试。";
        }
    }
}
