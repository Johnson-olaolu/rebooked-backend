package com.personal.rebooked.user.models;

import com.mongodb.client.model.geojson.Point;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document
public class Address {
    @Id
    private String id;
    private String state;
    private String country;
    private String formattedAddress;

    @GeoSpatialIndexed
    private Point location;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
