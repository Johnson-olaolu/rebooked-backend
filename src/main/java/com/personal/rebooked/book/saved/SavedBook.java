package com.personal.rebooked.book.saved;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/** A book a user saved (the heart button). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document("saved_books")
@CompoundIndexes({
        @CompoundIndex(name = "user_book", def = "{'userId': 1, 'bookId': 1}", unique = true),
        @CompoundIndex(name = "user_created", def = "{'userId': 1, 'createdAt': -1}")
})
public class SavedBook {
    @Id
    private String id;
    private String userId;
    @Indexed
    private String bookId;
    private Date createdAt;
}
