package no.hyttebok.chat;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import jakarta.validation.Valid;

@Controller("/api/chat")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @Post
    public ChatService.Reply chat(@Body @Valid ChatService.Request request) {
        return chat.reply(request);
    }
}
