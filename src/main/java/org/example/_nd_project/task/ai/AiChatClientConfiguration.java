package org.example._nd_project.task.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Spring AI's generic ChatClient auto-configuration is disabled for the normal
 * database profile so an API key is never required to start the application.
 * When the optional ai-google profile creates a ChatModel, this supplies the
 * builder used by the task-draft and worker-recommendation features.
 */
@Configuration
@Profile("ai-google")
@ConditionalOnBean(ChatModel.class)
public class AiChatClientConfiguration {

    @Bean
    ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }
}
