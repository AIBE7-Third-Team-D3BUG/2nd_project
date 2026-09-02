package org.example._nd_project;

import org.example._nd_project.notification.MemberNotificationRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@Transactional(readOnly = true)
@Tag("supabase")
class MemberNotificationRepositorySupabaseTest {

    @Autowired MemberNotificationRepository notificationRepository;

    @Test
    void notificationQueriesExecuteAgainstPostgresql() {
        assertThat(notificationRepository
                .findTop20ByMemberIdOrderByCreatedAtDescIdDesc(-1L)).isEmpty();
        assertThat(notificationRepository.countByMemberIdAndReadAtIsNull(-1L)).isZero();
    }
}
