package org.example._nd_project;

import org.example._nd_project.chat.ChatMessageRepository;
import org.example._nd_project.member.Member;
import org.example._nd_project.member.MemberRepository;
import org.example._nd_project.submission.ReviewRepository;
import org.example._nd_project.task.TaskCategory;
import org.example._nd_project.task.TaskRepository;
import org.example._nd_project.task.TaskStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@ImportAutoConfiguration(exclude = ChatClientAutoConfiguration.class)
@ActiveProfiles("db")
@Tag("supabase")
@Transactional(readOnly = true)
class WorkerRecommendationRepositorySupabaseTest {

    @Autowired MemberRepository memberRepository;
    @Autowired TaskRepository taskRepository;
    @Autowired ReviewRepository reviewRepository;
    @Autowired ChatMessageRepository chatMessageRepository;

    @Test
    void recommendationAggregateQueriesRunAgainstCurrentSchema() {
        List<Long> memberIds = memberRepository.findAll().stream().map(Member::getId).limit(20).toList();
        if (memberIds.isEmpty()) {
            return;
        }

        assertDoesNotThrow(() -> taskRepository.countCompletedByWorkersAndCategory(
                memberIds, TaskCategory.DEVELOPMENT
        ));
        assertDoesNotThrow(() -> taskRepository.countActiveByWorkers(
                memberIds,
                List.of(TaskStatus.MATCHED, TaskStatus.IN_PROGRESS, TaskStatus.SUBMITTED, TaskStatus.DISPUTED)
        ));
        assertDoesNotThrow(() -> reviewRepository.findDeadlineMetrics(memberIds));
        assertDoesNotThrow(() -> chatMessageRepository.findWorkerResponseMetrics(memberIds));
    }
}
