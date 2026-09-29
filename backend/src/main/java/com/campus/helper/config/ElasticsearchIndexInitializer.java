package com.campus.helper.config;

import com.campus.helper.entity.OrderDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates the {@code campus-orders} index from the mapping declared on {@link OrderDocument},
 * and reports an existing index whose mapping does not match that declaration.
 *
 * <p>The index is written to through the low-level {@code ElasticsearchClient}, which relies
 * on dynamic mapping. Elasticsearch's dynamic mapping turns a {@code "lat,lon"} string into
 * text rather than {@code geo_point}, which leaves the {@code geo_distance} and
 * {@code geohash_grid} queries failing against an index nobody created explicitly.
 *
 * <p>This runner never deletes data. A mismatched mapping is reported and left alone, since
 * the operator — not the application — should decide when the index is worth rebuilding.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchIndexInitializer implements ApplicationRunner {

    /** Fields whose mapped type must match {@link OrderDocument}, keyed by the required type. */
    private static final Map<String, String> REQUIRED_TYPES = new LinkedHashMap<>();

    static {
        REQUIRED_TYPES.put("pickupGeoPoint", "geo_point");
        REQUIRED_TYPES.put("deliveryGeoPoint", "geo_point");
        REQUIRED_TYPES.put("createdAt", "date");
        REQUIRED_TYPES.put("updatedAt", "date");
        REQUIRED_TYPES.put("completedAt", "date");
    }

    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public void run(ApplicationArguments args) {
        try {
            IndexOperations indexOps = elasticsearchOperations.indexOps(OrderDocument.class);
            String indexName = indexOps.getIndexCoordinates().getIndexName();

            if (!indexOps.exists()) {
                indexOps.createWithMapping();
                log.info("[ES索引] 已按 {} 的注解创建索引 {} 及映射", OrderDocument.class.getSimpleName(), indexName);
                return;
            }
            reportMappingMismatches(indexOps, indexName);
        } catch (Exception e) {
            // Search is a derived capability; a cold Elasticsearch must not stop the application.
            log.warn("[ES索引] 初始化或校验失败，订单搜索可能不可用: {}", e.getMessage());
        }
    }

    private void reportMappingMismatches(IndexOperations indexOps, String indexName) {
        Map<String, Object> mapping = indexOps.getMapping();
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> required : REQUIRED_TYPES.entrySet()) {
            String actual = fieldType(mapping, required.getKey());
            if (!required.getValue().equals(actual)) {
                problems.add(required.getKey() + " 期望 " + required.getValue()
                        + "、实际 " + (actual == null ? "缺失" : actual));
            }
        }
        if (problems.isEmpty()) {
            log.info("[ES索引] 索引 {} 映射校验通过", indexName);
            return;
        }
        log.warn("[ES索引] 索引 {} 的映射与 {} 声明不一致: {}。"
                        + "geo 查询与日期聚合会失败或结果不正确；"
                        + "确认数据可由 MySQL 重建后，执行 DELETE /{} 并重启以重建索引。",
                indexName, OrderDocument.class.getSimpleName(), String.join("; ", problems), indexName);
    }

    /** Read {@code properties.<field>.type} out of a mapping document, or null when absent. */
    private String fieldType(Map<String, Object> mapping, String field) {
        if (mapping == null || !(mapping.get("properties") instanceof Map<?, ?> properties)) {
            return null;
        }
        if (!(properties.get(field) instanceof Map<?, ?> fieldDef)) {
            return null;
        }
        Object type = fieldDef.get("type");
        return type == null ? null : type.toString();
    }
}
