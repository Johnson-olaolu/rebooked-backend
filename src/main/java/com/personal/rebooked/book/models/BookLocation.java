package com.personal.rebooked.book.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The seller's state and city, copied onto each book so the shop can filter by location. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookLocation {
    private String state;
    private String city;
}
