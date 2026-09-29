package com.campus.helper.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.*;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.campus.helper.entity.OrderDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.campus.helper.common.BusinessException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Order search service backed by Elasticsearch.
 *
 * Capabilities:
 * <ul>
 *   <li>Full-text search across title, description, pickup/delivery locations</li>
 *   <li>Geo-distance filter — find orders within N km of a point</li>
 *   <li>Multi-faceted filtering — status, orderType, date range, amount range</li>
 *   <li>Aggregations — by status, by order type, by day (date histogram)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSearchService {

    private final ElasticsearchClient esClient;

    private static final String INDEX_NAME = "campus-orders";

    /**
     * Index a new order document into ES.
     */
    public void index(OrderDocument doc) {
        try {
            IndexResponse response = esClient.index(i -> i
                    .index(INDEX_NAME)
                    .id(String.valueOf(doc.getId()))
                    .document(doc));
            log.debug("ES indexed order {}: result={}", doc.getId(), response.result());
        } catch (Exception e) {
            log.error("Failed to index order {} to ES", doc.getId(), e);
            throw new IllegalStateException("Failed to index order " + doc.getId(), e);
        }
    }

    /**
     * Update an existing order document in ES.
     */
    public void update(OrderDocument doc) {
        try {
            UpdateResponse<OrderDocument> response = esClient.update(u -> u
                    .index(INDEX_NAME)
                    .id(String.valueOf(doc.getId()))
                    .doc(doc), OrderDocument.class);
            log.debug("ES updated order {}: result={}", doc.getId(), response.result());
        } catch (Exception e) {
            log.error("Failed to update order {} in ES: {}", doc.getId(), e.getMessage());
        }
    }

    /**
     * Delete an order document from ES.
     */
    public void delete(Long orderId) {
        try {
            esClient.delete(d -> d.index(INDEX_NAME).id(String.valueOf(orderId)));
            log.debug("ES deleted order {}", orderId);
        } catch (Exception e) {
            log.error("Failed to delete order {} from ES: {}", orderId, e.getMessage());
        }
    }

    /**
     * Full-text search with optional filters and geo constraint.
     *
     * @param keyword   search keyword (null for no full-text)
     * @param status    filter by status (null for all)
     * @param orderType filter by order type (null for all)
     * @param lat       latitude for geo filter (null to skip)
     * @param lon       longitude for geo filter (null to skip)
     * @param distanceKm distance in km for geo filter
     * @param from      pagination offset
     * @param size      page size
     * @return search result with hits and metadata
     */
    public SearchResult search(String keyword, Integer status, Integer orderType,
                               Double lat, Double lon, Double distanceKm,
                               int from, int size) {
        try {
            BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

            // Full-text search
            if (keyword != null && !keyword.isBlank()) {
                boolBuilder.must(Query.of(q -> q
                        .multiMatch(mm -> mm
                                .query(keyword)
                                .fields("title^3", "description^2",
                                        "pickupLocation^2", "deliveryLocation^2")
                        )));
            }

            // Status filter
            if (status != null) {
                boolBuilder.filter(Query.of(q -> q
                        .term(t -> t.field("status").value(status))));
            }

            // Order type filter
            if (orderType != null) {
                boolBuilder.filter(Query.of(q -> q
                        .term(t -> t.field("orderType").value(orderType))));
            }

            // Geo-distance filter
            if (lat != null && lon != null && distanceKm != null && distanceKm > 0) {
                boolBuilder.filter(Query.of(q -> q
                        .geoDistance(gd -> gd
                                .field("pickupGeoPoint")
                                .location(l -> l.latlon(ll -> ll.lat(lat).lon(lon)))
                                .distance(distanceKm + "km")
                        )));
            }

            SearchRequest request = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .query(q -> q.bool(boolBuilder.build()))
                    .from(from)
                    .size(size)
                    .sort(so -> so.field(f -> f.field("createdAt").order(SortOrder.Desc)))
                    .trackTotalHits(th -> th.enabled(true))
            );

            SearchResponse<OrderDocument> response = esClient.search(request, OrderDocument.class);

            return SearchResult.builder()
                    .total(response.hits().total() != null ? response.hits().total().value() : 0)
                    .hits(response.hits().hits().stream()
                            .map(Hit::source)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList()))
                    .build();

        } catch (Exception e) {
            log.error("ES search failed", e);
            throw new BusinessException(503, "订单搜索服务暂时不可用");
        }
    }

    /**
     * Get aggregation stats: count by status, count by order type, and daily revenue trend.
     *
     * @param days number of days for the date histogram
     * @return aggregation map
     */
    public Map<String, Object> aggregations(int days) {
        try {
            SearchRequest request = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .size(0) // no docs, only aggs
                    .query(q -> q.range(r -> r
                            .field("createdAt")
                            .gte(JsonData.of("now-" + days + "d/d"))))
                    .aggregations("by_status", a -> a
                            .terms(t -> t.field("status").size(10)))
                    .aggregations("by_order_type", a -> a
                            .terms(t -> t.field("orderType").size(10)))
                    .aggregations("daily_revenue", a -> a
                            .dateHistogram(dh -> dh
                                    .field("createdAt")
                                    .calendarInterval(CalendarInterval.Day)
                                    .format("yyyy-MM-dd")
                                    .minDocCount(0)
                            )
                            .aggregations("revenue", sa -> sa
                                    .sum(sum -> sum.field("amount")))
                            .aggregations("tip_total", sa -> sa
                                    .sum(sum -> sum.field("tip")))
                            .aggregations("order_count", sa -> sa
                                    .valueCount(vc -> vc.field("id")))
                    )
            );

            SearchResponse<Void> response = esClient.search(request, Void.class);

            Map<String, Object> result = new LinkedHashMap<>();

            // By status
            Aggregate byStatus = response.aggregations().get("by_status");
            if (byStatus != null && byStatus.isSterms()) {
                List<Map<String, Object>> statusBuckets = new ArrayList<>();
                for (StringTermsBucket bucket : byStatus.sterms().buckets().array()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("status", bucket.key());
                    entry.put("count", bucket.docCount());
                    statusBuckets.add(entry);
                }
                result.put("byStatus", statusBuckets);
            }

            // By order type
            Aggregate byType = response.aggregations().get("by_order_type");
            if (byType != null && byType.isSterms()) {
                List<Map<String, Object>> typeBuckets = new ArrayList<>();
                for (StringTermsBucket bucket : byType.sterms().buckets().array()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("orderType", bucket.key());
                    entry.put("count", bucket.docCount());
                    typeBuckets.add(entry);
                }
                result.put("byOrderType", typeBuckets);
            }

            // Daily revenue trend
            Aggregate dailyRevenue = response.aggregations().get("daily_revenue");
            if (dailyRevenue != null && dailyRevenue.isDateHistogram()) {
                List<Map<String, Object>> trend = new ArrayList<>();
                for (DateHistogramBucket bucket : dailyRevenue.dateHistogram().buckets().array()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("date", bucket.keyAsString());

                    Aggregate revAgg = bucket.aggregations().get("revenue");
                    entry.put("revenue", revAgg != null && revAgg.isSum()
                            ? revAgg.sum().value() : 0.0);

                    Aggregate tipAgg = bucket.aggregations().get("tip_total");
                    entry.put("tipTotal", tipAgg != null && tipAgg.isSum()
                            ? tipAgg.sum().value() : 0.0);

                    Aggregate countAgg = bucket.aggregations().get("order_count");
                    entry.put("orderCount", countAgg != null && countAgg.isValueCount()
                            ? (long) countAgg.valueCount().value() : 0L);

                    trend.add(entry);
                }
                result.put("dailyTrend", trend);
            }

            return result;

        } catch (Exception e) {
            log.error("ES aggregation failed", e);
            throw new BusinessException(503, "订单统计服务暂时不可用");
        }
    }

    // ── BI Dashboard Aggregations ───────────────────────

    /**
     * Comprehensive BI dashboard data.
     * Runs multiple aggregation buckets in a single ES query.
     *
     * @param days lookback window (e.g. 7, 30)
     * @return dashboard map with all dimensions
     */
    public Map<String, Object> biDashboard(int days) {
        try {
            SearchRequest request = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .size(0)
                    .query(q -> q.range(r -> r
                            .field("createdAt")
                            .gte(JsonData.of("now-" + days + "d/d"))))
                    // Dimension 1: status breakdown
                    .aggregations("by_status", a -> a
                            .terms(t -> t.field("status").size(10)))
                    // Dimension 2: order type breakdown
                    .aggregations("by_type", a -> a
                            .terms(t -> t.field("orderType").size(10)))
                    // Dimension 3: daily revenue trend
                    .aggregations("daily_trend", a -> a
                            .dateHistogram(dh -> dh
                                    .field("createdAt")
                                    .calendarInterval(CalendarInterval.Day)
                                    .format("MM/dd")
                                    .minDocCount(0))
                            .aggregations("revenue", sa -> sa
                                    .sum(sum -> sum.field("amount")))
                            .aggregations("tip_total", sa -> sa
                                    .sum(sum -> sum.field("tip")))
                            .aggregations("order_count", sa -> sa
                                    .valueCount(vc -> vc.field("id"))))
                    // Dimension 4: hourly peak distribution
                    .aggregations("hourly_peak", a -> a
                            .dateHistogram(dh -> dh
                                    .field("createdAt")
                                    .fixedInterval(t -> t.time("1h"))
                                    .format("HH:00")
                                    .minDocCount(0))
                            .aggregations("hour_count", sa -> sa
                                    .valueCount(vc -> vc.field("id"))))
                    // Dimension 5: revenue stats (total, avg, min, max)
                    .aggregations("total_revenue", a -> a
                            .sum(sum -> sum.field("amount")))
                    .aggregations("avg_order", a -> a
                            .avg(av -> av.field("amount")))
                    // Dimension 6: geohash grid for heatmap
                    .aggregations("geo_heatmap", a -> a
                            .geohashGrid(gh -> gh
                                    .field("pickupGeoPoint")
                                    .size(500)))
            );

            SearchResponse<Void> response = esClient.search(request, Void.class);
            Map<String, Object> result = new LinkedHashMap<>();

            // Total revenue
            Aggregate totalRev = response.aggregations().get("total_revenue");
            result.put("totalRevenue", totalRev != null && totalRev.isSum()
                    ? totalRev.sum().value() : 0.0);

            // Average order amount
            Aggregate avgOrd = response.aggregations().get("avg_order");
            result.put("avgOrderAmount", avgOrd != null && avgOrd.isAvg()
                    ? avgOrd.avg().value() : 0.0);

            // Status breakdown
            result.put("byStatus", extractTermBuckets(
                    response.aggregations().get("by_status"), "status", "count"));

            // Order type breakdown
            result.put("byType", extractTermBuckets(
                    response.aggregations().get("by_type"), "orderType", "count"));

            // Daily trend
            result.put("dailyTrend", extractDateHistogram(
                    response.aggregations().get("daily_trend")));

            // Hourly peak
            result.put("hourlyPeak", extractHourlyPeak(
                    response.aggregations().get("hourly_peak")));

            // Geo heatmap
            result.put("geoHeatmap", extractGeohashGrid(
                    response.aggregations().get("geo_heatmap")));

            return result;

        } catch (Exception e) {
            log.error("BI dashboard aggregation failed", e);
            throw new BusinessException(503, "BI统计服务暂时不可用");
        }
    }

    /**
     * Narrow-scope aggregation: status distribution only.
     */
    public List<Map<String, Object>> statusDistribution(int days) {
        try {
            SearchRequest request = SearchRequest.of(s -> s
                    .index(INDEX_NAME).size(0)
                    .query(q -> q.range(r -> r
                            .field("createdAt").gte(JsonData.of("now-" + days + "d/d"))))
                    .aggregations("by_status", a -> a
                            .terms(t -> t.field("status").size(10))));

            SearchResponse<Void> response = esClient.search(request, Void.class);
            return extractTermBuckets(
                    response.aggregations().get("by_status"), "status", "count");

        } catch (Exception e) {
            log.error("Status distribution failed", e);
            throw new BusinessException(503, "订单状态统计暂时不可用");
        }
    }

    /**
     * Narrow-scope aggregation: revenue trend (day/hour).
     */
    public List<Map<String, Object>> revenueTrend(int days, String interval) {
        try {
            CalendarInterval calInterval = "week".equals(interval)
                    ? CalendarInterval.Day : CalendarInterval.Day;

            SearchRequest request = SearchRequest.of(s -> s
                    .index(INDEX_NAME).size(0)
                    .query(q -> q.range(r -> r
                            .field("createdAt").gte(JsonData.of("now-" + days + "d/d"))))
                    .aggregations("trend", a -> a
                            .dateHistogram(dh -> {
                                DateHistogramAggregation.Builder b = dh
                                        .field("createdAt")
                                        .format("MM/dd")
                                        .minDocCount(0);
                                if ("day".equals(interval)) {
                                    b.fixedInterval(t -> t.time("1h"));
                                } else {
                                    b.calendarInterval(calInterval);
                                }
                                return b;
                            })
                            .aggregations("revenue", sa -> sa.sum(sum -> sum.field("amount")))
                            .aggregations("order_count", sa -> sa.valueCount(vc -> vc.field("id")))));

            SearchResponse<Void> response = esClient.search(request, Void.class);
            return extractDateHistogram(response.aggregations().get("trend"));

        } catch (Exception e) {
            log.error("Revenue trend failed", e);
            throw new BusinessException(503, "收入趋势统计暂时不可用");
        }
    }

    /**
     * Geo-heatmap: geohash_grid aggregation of order pickup locations.
     */
    public List<Map<String, Object>> geoHeatmap(int days) {
        try {
            SearchRequest request = SearchRequest.of(s -> s
                    .index(INDEX_NAME).size(0)
                    .query(q -> q.bool(b -> b
                            .must(m -> m.range(r -> r
                                    .field("createdAt").gte(JsonData.of("now-" + days + "d/d"))))
                            .must(m -> m.exists(e -> e.field("pickupGeoPoint")))))
                    .aggregations("heatmap", a -> a
                            .geohashGrid(gh -> gh
                                    .field("pickupGeoPoint")
                                    .size(500))));

            SearchResponse<Void> response = esClient.search(request, Void.class);
            return extractGeohashGrid(response.aggregations().get("heatmap"));

        } catch (Exception e) {
            log.error("Geo heatmap failed", e);
            throw new BusinessException(503, "地理热力图暂时不可用");
        }
    }

    // ── Private extraction helpers ──────────────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractTermBuckets(Aggregate agg,
                                                          String keyName, String countName) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (agg != null && agg.isSterms()) {
            for (StringTermsBucket bucket : agg.sterms().buckets().array()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put(keyName, parseFieldValue(bucket.key()));
                entry.put(countName, bucket.docCount());
                result.add(entry);
            }
        }
        return result;
    }

    private List<Map<String, Object>> extractDateHistogram(Aggregate agg) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (agg != null && agg.isDateHistogram()) {
            for (DateHistogramBucket bucket : agg.dateHistogram().buckets().array()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("date", bucket.keyAsString());

                Aggregate revAgg = bucket.aggregations().get("revenue");
                entry.put("revenue", revAgg != null && revAgg.isSum()
                        ? revAgg.sum().value() : 0.0);

                Aggregate countAgg = bucket.aggregations().get("order_count");
                entry.put("orderCount", countAgg != null && countAgg.isValueCount()
                        ? (long) countAgg.valueCount().value() : 0L);

                Aggregate tipAgg = bucket.aggregations().get("tip_total");
                if (tipAgg != null && tipAgg.isSum()) {
                    entry.put("tipTotal", tipAgg.sum().value());
                }
                result.add(entry);
            }
        }
        return result;
    }

    private List<Map<String, Object>> extractHourlyPeak(Aggregate agg) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (agg != null && agg.isDateHistogram()) {
            for (DateHistogramBucket bucket : agg.dateHistogram().buckets().array()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("hour", bucket.keyAsString());

                Aggregate countAgg = bucket.aggregations().get("hour_count");
                entry.put("count", countAgg != null && countAgg.isValueCount()
                        ? (long) countAgg.valueCount().value() : 0L);
                result.add(entry);
            }
        }
        return result;
    }

    private List<Map<String, Object>> extractGeohashGrid(Aggregate agg) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (agg != null && agg.isGeohashGrid()) {
            for (var bucket : agg.geohashGrid().buckets().array()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("geohash", bucket.key());
                entry.put("count", bucket.docCount());
                result.add(entry);
            }
        }
        return result;
    }

    /** Parse a FieldValue to a numeric type (int/long) or fall back to string. */
    private Object parseFieldValue(co.elastic.clients.elasticsearch._types.FieldValue fv) {
        if (fv == null) return null;
        if (fv.isLong()) return fv.longValue();
        if (fv.isDouble()) return fv.doubleValue();
        return fv.stringValue();
    }

    /**
     * Search result wrapper.
     */
    @lombok.Data
    @lombok.Builder
    public static class SearchResult {
        private long total;
        private List<OrderDocument> hits;
    }
}
