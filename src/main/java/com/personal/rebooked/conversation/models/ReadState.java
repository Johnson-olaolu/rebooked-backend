package com.personal.rebooked.conversation.models;

import lombok.Data;

import java.util.Date;

@Data
public class ReadState {
    private String lastReadMessageId;
    private Date lastReadAt;
    private int unread;
}
