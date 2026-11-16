package com.personal.rebooked.rating;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/** A buyer's rating of the seller for one sale. One per (book, buyer). */
@Data
@Document("seller_ratings")
@CompoundIndexes({
        @CompoundIndex(name = "book_buyer_unique", def = "{'bookId': 1, 'buyerId': 1}", unique = true),
        @CompoundIndex(name = "seller_created", def = "{'sellerId': 1, 'createdAt': -1}")
})
public class SellerRating {
    @Id
    private String id;
    private String sellerId;
    private String buyerId;
    private String bookId;
    private int stars;
    private String comment;
    private Date createdAt;
    private Date updatedAt;
}
