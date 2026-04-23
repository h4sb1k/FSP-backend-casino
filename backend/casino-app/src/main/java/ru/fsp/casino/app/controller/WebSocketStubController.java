package ru.fsp.casino.app.controller;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class WebSocketStubController {

    @MessageMapping("/room/{roomId}/ping")
    @SendTo("/topic/room/{roomId}")
    public Map<String, Object> ping(@DestinationVariable String roomId) {
        return Map.of(
            "type", "PONG",
            "roomId", roomId,
            "timestamp", System.currentTimeMillis()
        );
    }
}
