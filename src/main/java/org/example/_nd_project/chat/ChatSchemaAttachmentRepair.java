package org.example._nd_project.chat;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
public class ChatSchemaAttachmentRepair {

    private final JdbcTemplate jdbcTemplate;

    public ChatSchemaAttachmentRepair(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @jakarta.annotation.PostConstruct
    public void repair() {
        jdbcTemplate.execute("""
                ALTER TABLE chat_messages
                    ADD COLUMN IF NOT EXISTS attachment_name VARCHAR(255)
                """);
        jdbcTemplate.execute("""
                ALTER TABLE chat_messages
                    ADD COLUMN IF NOT EXISTS attachment_path VARCHAR(1500)
                """);
        jdbcTemplate.execute("""
                ALTER TABLE chat_messages
                    ADD COLUMN IF NOT EXISTS attachment_size BIGINT
                """);
    }
}
