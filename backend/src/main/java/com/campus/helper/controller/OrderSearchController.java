package com.campus.helper.controller;

import com.campus.helper.common.Result;
import com.campus.helper.service.OrderSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Elasticsearch-powered order search API.
 *
 * Endpoints:
 * <ul>
 *   <li>GET /api/search/orders — full-text + filter + geo search</li>
 *   <li>GET /api/search/orders/aggregations — dashboards (BI)</li>
 *   <li>GET /api/search/orders/nearby — geo-distance search</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/search/orders")
@RequiredArgsConstructor
public class OrderSearchController {

    private final OrderSearchService orderSearchService;

    /**
     * Full-text search with filters.
     *
     * @param keyword    search keyword (title, description, locations)
     * @param status     filter by status
     * @param orderType  filter by order type
     * @param lat        latitude for geo-distance filter
     * @param lon        longitude for geo-distance filter
     * @param distanceKm radius in km (default 5)
     * @param page       page number (0-based)
     * @param size       page size (default 10)
     */
    @GetMapping
    public Result search(@RequestParam(required = false) String keyword,
                         @RequestParam(required = false) Integer status,
                         @RequestParam(required = false) Integer orderType,
                         @RequestParam(required = false) Double lat,
                         @RequestParam(required = false) Double lon,
                         @RequestParam(required = false, defaultValue = "5") Double distanceKm,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "10") int size) {
        OrderSearchService.SearchResult result = orderSearchService.search(
                keyword, status, orderType, lat, lon, distanceKm, page * size, size);
        return Result.success(Map.of(
                "total", result.getTotal(),
                "page", page,
                "size", size,
                "orders", result.getHits()
        ));
    }

    /**
     * Geo-distance search — find orders near a point.
     * Convenience endpoint for "near me" queries.
     */
    @GetMapping("/nearby")
    public Result nearby(@RequestParam Double lat,
                         @RequestParam Double lon,
                         @RequestParam(defaultValue = "3") Double radiusKm,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size) {
        OrderSearchService.SearchResult result = orderSearchService.search(
                null, null, null, lat, lon, radiusKm, page * size, size);
        return Result.success(Map.of(
                "total", result.getTotal(),
                "radiusKm", radiusKm,
                "orders", result.getHits()
        ));
    }

    /**
     * Aggregation stats for dashboards (BI).
     *
     * @param days number of days to aggregate (default 30)
     */
    @GetMapping("/aggregations")
    public Result aggregations(@RequestParam(defaultValue = "30") int days) {
        Map<String, Object> aggs = orderSearchService.aggregations(days);
        return Result.success(aggs);
    }
}
