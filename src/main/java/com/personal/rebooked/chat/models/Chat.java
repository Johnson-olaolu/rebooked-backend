package com.personal.rebooked.chat.models;

import com.personal.rebooked.book.models.Book;
import com.personal.rebooked.file.models.File;
import com.personal.rebooked.user.models.Address;
import com.personal.rebooked.user.models.User;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class Chat {
    @Id
    private String id;

   @DBRef
   private User seller;

   @DBRef
   private User buyer;

   @DBRef
   private Book book;

   @DBRef
   private List<Message> message;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
