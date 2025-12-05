package com.kirisamemarisa.blog.ai;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiClientService aiClientService;

    public AiController(AiClientService aiClientService) {
        this.aiClientService = aiClientService;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> payload) {
        String message = payload.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
        }
        String reply = aiClientService.chat(message);
        return ResponseEntity.ok(Map.of("reply", reply));
    }

    /**
     * Simple SSE endpoint that streams the reply in small chunks.
     * This does not use upstream streaming; it splits the final reply for better
     * UX.
     */
    @org.springframework.web.bind.annotation.GetMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@org.springframework.web.bind.annotation.RequestParam("message") String message) {
        SseEmitter emitter = new SseEmitter(0L);
        if (message == null || message.isBlank()) {
            try {
                emitter.send(SseEmitter.event().data("{" + "\"error\":\"message is required\"" + "}"));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        new Thread(() -> {
            try {
                String reply = aiClientService.chat(message);
                if (reply == null)
                    reply = "";
                // naive chunking: split by sentence delimiters; fallback to small substrings
                String[] parts = reply.split("(?<=[。！？!?.])");
                if (parts.length == 0)
                    parts = new String[] { reply };
                for (String part : parts) {
                    String p = part;
                    if (p == null)
                        continue;
                    // further chunk if too long
                    int chunkSize = 64;
                    for (int i = 0; i < p.length(); i += chunkSize) {
                        String sub = p.substring(i, Math.min(p.length(), i + chunkSize));
                        emitter.send(SseEmitter.event().data(sub));
                        Thread.sleep(50L);
                    }
                }
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().data("[error] " + e.getMessage()));
                } catch (Exception ignored) {
                }
                emitter.completeWithError(e);
            }
        }, "ai-chat-stream").start();

        return emitter;
    }
}
