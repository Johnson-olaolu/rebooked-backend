package com.personal.rebooked.chat.models;

import com.personal.rebooked.utils.Constants;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document
public class Message {

    @Id
    private String id;
    private  String senderId;
    private String receiverId;
    private String Message;
    private  Constants.ChatType chatType;
    private boolean isDelivered;
    private boolean isSeen;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
