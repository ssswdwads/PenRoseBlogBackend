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
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> payload) {
        Object m = payload.get("message");
        String message = m == null ? null : String.valueOf(m);
        String model = null;
        Object md = payload.get("model");
        if (md != null)
            model = String.valueOf(md);
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
        }
        String reply = aiClientService.chat(message, model);
        return ResponseEntity.ok(Map.of("reply", reply));
    }

    /**
     * Chat with optional attachments (images or text). Body example:
     * {
     * "message": "explain this",
     * "attachments": [
     * {"mime":"image/png","name":"a.png","dataUrl":"data:image/png;base64,..."},
     * {"mime":"text/plain","name":"note.txt","text":"..."}
     * ]
     * }
     */
    @PostMapping(path = "/chat/attachments", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> chatWithAttachments(@RequestBody Map<String, Object> payload) {
        Object m = payload.get("message");
        String message = m == null ? null : String.valueOf(m);
        String model = null;
        Object md = payload.get("model");
        if (md != null)
            model = String.valueOf(md);
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
        }
        Object atts = payload.get("attachments");
        java.util.List<java.util.Map<String, Object>> attachments = java.util.Collections.emptyList();
        if (atts instanceof java.util.List<?> list) {
            attachments = new java.util.ArrayList<>();
            for (Object o : list) {
                if (o instanceof java.util.Map<?, ?> mm) {
                    java.util.Map<String, Object> one = new java.util.HashMap<>();
                    mm.forEach((k, v) -> one.put(String.valueOf(k), v));
                    attachments.add(one);
                }
            }
        }
        String reply = aiClientService.chatWithAttachments(message, attachments, model);
        return ResponseEntity.ok(Map.of("reply", reply));
    }

    /**
     * Simple SSE endpoint that streams the reply in small chunks.
     * This does not use upstream streaming; it splits the final reply for better
     * UX.
     */
    @org.springframework.web.bind.annotation.GetMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@org.springframework.web.bind.annotation.RequestParam("message") String message,
            @org.springframework.web.bind.annotation.RequestParam(value = "model", required = false) String model) {
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
                String reply = aiClientService.chat(message, model);
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
