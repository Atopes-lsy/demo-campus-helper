package com.campus.helper.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.math.BigDecimal;

/**
 * Elasticsearch document mapped to the "orders" index.
 * Supports full-text search, geo queries, and aggregations.
 *
 * Index settings: 3 shards, 1 replica for campus scale.
 * Using ik_max_word analyzer for Chinese text tokenization when available.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "campus-orders")
@Setting(shards = 3, replicas = 1)
public class OrderDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Keyword)
    private String orderNo;

    @Field(type = FieldType.Long)
    private Long studentId;

    @Field(type = FieldType.Long)
    private Long runnerId;

    @Field(type = FieldType.Integer)
    private Integer orderType;

    @Field(type = FieldType.Integer)
    private Integer status;

    /** Full-text searchable title */
    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;

    /** Full-text searchable description */
    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    /** Searchable pickup location name */
    @Field(type = FieldType.Text, analyzer = "standard")
    private String pickupLocation;

    /** Searchable delivery location name */
    @Field(type = FieldType.Text, analyzer = "standard")
    private String deliveryLocation;

    /** Geo point for pickup — enables "near me" queries */
    @GeoPointField
    private String pickupGeoPoint;

    /** Geo point for delivery */
    @GeoPointField
    private String deliveryGeoPoint;

    @Field(type = FieldType.Double)
    private BigDecimal amount;

    @Field(type = FieldType.Double)
    private BigDecimal tip;

    @Field(type = FieldType.Keyword)
    private String packages;

    @Field(type = FieldType.Keyword)
    private String pickupCode;

    @Field(type = FieldType.Date)
    private String createdAt;

    @Field(type = FieldType.Date)
    private String updatedAt;

    @Field(type = FieldType.Date)
    private String completedAt;
}
