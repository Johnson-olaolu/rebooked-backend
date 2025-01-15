package com.personal.rebooked.chat;

import com.personal.rebooked.chat.dto.MessageDto;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class ChatGateway {

    @MessageMapping("/chat")
    @SendTo("/topic/chat")
    public  void sendChat (@Payload MessageDto message) {

    }
}
