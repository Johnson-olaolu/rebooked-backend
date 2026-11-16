package com.personal.rebooked.conversation.models;

import com.personal.rebooked.utils.Constants;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LastMessage {
    private String id;
    /** At most 140 characters. */
    private String preview;
    private String senderId;
    private String bookId;
    private Constants.MessageType type;
    private Date createdAt;
}
