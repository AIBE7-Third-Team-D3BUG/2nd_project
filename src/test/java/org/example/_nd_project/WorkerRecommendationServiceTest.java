package org.example._nd_project;

import org.example._nd_project.chat.ChatMessageRepository;
import org.example._nd_project.member.Member;
import org.example._nd_project.member.MemberRepository;
import org.example._nd_project.submission.ReviewRepository;
import org.example._nd_project.submission.WorkerDelayMetrics;
import org.example._nd_project.submission.WorkerDelayMetricsService;
import org.example._nd_project.task.Task;
import org.example._nd_project.task.TaskCategory;
import org.example._nd_project.task.TaskRepository;
import org.example._nd_project.volunteer.Volunteer;
import org.example._nd_project.volunteer.VolunteerRepository;
import org.example._nd_project.volunteer.VolunteerStatus;
import org.example._nd_project.volunteer.WorkerRecommendationService;
import org.example._nd_project.volunteer.ai.AiWorkerRecommendationItem;
import org.example._nd_project.volunteer.ai.AiWorkerRecommendationReport;
import org.example._nd_project.volunteer.ai.WorkerRecommendationAiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerRecommendationServiceTest {

    @Mock VolunteerRepository volunteerRepository;
    @Mock TaskRepository taskRepository;
    @Mock MemberRepository memberRepository;
    @Mock ReviewRepository reviewRepository;
    @Mock ChatMessageRepository chatMessageRepository;
    @Mock WorkerRecommendationAiClient aiClient;
    @Mock WorkerDelayMetricsService workerDelayMetricsService;

    private WorkerRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new WorkerRecommendationService(
                volunteerRepository, taskRepository, memberRepository,
                reviewRepository, chatMessageRepository, aiClient, workerDelayMetricsService
        );
    }

    @Test
    void ranksApplicantsUsingVerifiedMetricsAndAddsAiExplanation() {
        Task task = task();
        Volunteer strongApplication = application(11L, 2L, "2026-08-27T00:00:00Z");
        Volunteer weakApplication = application(12L, 3L, "2026-08-27T00:01:00Z");
        Member strongMember = member(2L, "강한지원자", new String[]{"AWS", "Spring Boot", "Nginx"}, 12, 8, 39);
        Member weakMember = member(3L, "신규지원자", new String[]{"Figma"}, 0, 0, 0);
        TaskRepository.WorkerCountMetric categoryMetric = countMetric(2L, 4);
        ReviewRepository.DeadlineMetric deadlineMetric = deadlineMetric(2L, 7, 8);
        ChatMessageRepository.WorkerResponseMetric responseMetric = responseMetric(2L, 300.0, 6);

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(volunteerRepository.findByTaskIdAndStatusOrderByCreatedAtAsc(10L, VolunteerStatus.APPLIED))
                .thenReturn(List.of(strongApplication, weakApplication));
        when(memberRepository.findAllById(List.of(2L, 3L))).thenReturn(List.of(strongMember, weakMember));
        when(taskRepository.countCompletedByWorkersAndCategory(List.of(2L, 3L), TaskCategory.DEVELOPMENT))
                .thenReturn(List.of(categoryMetric));
        when(taskRepository.countActiveByWorkers(org.mockito.ArgumentMatchers.eq(List.of(2L, 3L)), org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of());
        when(reviewRepository.findDeadlineMetrics(List.of(2L, 3L))).thenReturn(List.of(deadlineMetric));
        when(chatMessageRepository.findWorkerResponseMetrics(List.of(2L, 3L)))
                .thenReturn(List.of(responseMetric));
        when(workerDelayMetricsService.getForMembers(List.of(2L, 3L))).thenReturn(Map.of(
                2L, new WorkerDelayMetrics(90, 8, 7, 1, 0, 1),
                3L, WorkerDelayMetrics.empty(90)
        ));
        when(aiClient.analyze(anyString())).thenReturn(new AiWorkerRecommendationReport(List.of(
                new AiWorkerRecommendationItem("C11", "관련 경험과 기술 일치도가 높습니다.",
                        List.of("AWS·Spring·Nginx 기술 일치"), List.of("최종 일정 확인 필요"))
        )));

        var result = service.recommend(10L, 1L);

        assertEquals(2, result.size());
        assertEquals(11L, result.get(0).volunteerId());
        assertEquals(1, result.get(0).rank());
        assertEquals(100, result.get(0).skillMatchPercent());
        assertEquals(4, result.get(0).categoryCompletedCount());
        assertEquals("5분", result.get(0).averageResponseLabel());
        assertEquals(1, result.get(0).recentDelayPoints());
        assertEquals(88, result.get(0).recentDeadlineMetPercent());
        assertEquals("관찰", result.get(0).recentDelayStatus());
        assertTrue(result.get(0).aiEnhanced());
        assertTrue(result.get(0).suitabilityScore() > result.get(1).suitabilityScore());
    }

    @Test
    void fallsBackToDeterministicExplanationWhenAiFails() {
        Task task = task();
        Volunteer application = application(11L, 2L, "2026-08-27T00:00:00Z");
        Member member = member(2L, "지원자", new String[]{"AWS"}, 1, 0, 0);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(volunteerRepository.findByTaskIdAndStatusOrderByCreatedAtAsc(10L, VolunteerStatus.APPLIED))
                .thenReturn(List.of(application));
        when(memberRepository.findAllById(List.of(2L))).thenReturn(List.of(member));
        when(taskRepository.countCompletedByWorkersAndCategory(List.of(2L), TaskCategory.DEVELOPMENT)).thenReturn(List.of());
        when(taskRepository.countActiveByWorkers(org.mockito.ArgumentMatchers.eq(List.of(2L)), org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of());
        when(reviewRepository.findDeadlineMetrics(List.of(2L))).thenReturn(List.of());
        when(chatMessageRepository.findWorkerResponseMetrics(List.of(2L))).thenReturn(List.of());
        when(aiClient.analyze(anyString())).thenThrow(new IllegalStateException("AI unavailable"));

        var result = service.recommend(10L, 1L);

        assertEquals(1, result.size());
        assertTrue(!result.get(0).aiEnhanced());
        assertEquals("데이터 없음", result.get(0).averageResponseLabel());
    }

    @Test
    void nonRequesterCannotRequestRecommendation() {
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task()));

        assertThrows(ResponseStatusException.class, () -> service.recommend(10L, 99L));
    }

    private Task task() {
        Task task = Task.create(1L, "AWS 장애 복구", "설명", TaskCategory.DEVELOPMENT,
                new String[]{"AWS", "Spring", "Nginx"}, 60, Instant.now().plusSeconds(3600), "정상 접속", null);
        ReflectionTestUtils.setField(task, "id", 10L);
        return task;
    }

    private Volunteer application(Long id, Long memberId, String createdAt) {
        Volunteer volunteer = Volunteer.create(10L, memberId, "지원합니다.");
        ReflectionTestUtils.setField(volunteer, "id", id);
        ReflectionTestUtils.setField(volunteer, "createdAt", Instant.parse(createdAt));
        return volunteer;
    }

    private Member member(Long id, String nickname, String[] skills, int completed, int reviews, int ratingSum) {
        Member member = Member.register(nickname + "@example.com", "password", nickname, Instant.now());
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "skillTags", skills);
        ReflectionTestUtils.setField(member, "completedTaskCount", completed);
        ReflectionTestUtils.setField(member, "reviewCount", reviews);
        ReflectionTestUtils.setField(member, "ratingSum", ratingSum);
        return member;
    }

    private TaskRepository.WorkerCountMetric countMetric(Long memberId, long count) {
        TaskRepository.WorkerCountMetric metric = mock(TaskRepository.WorkerCountMetric.class);
        when(metric.getMemberId()).thenReturn(memberId);
        when(metric.getCount()).thenReturn(count);
        return metric;
    }

    private ReviewRepository.DeadlineMetric deadlineMetric(Long memberId, long met, long samples) {
        ReviewRepository.DeadlineMetric metric = mock(ReviewRepository.DeadlineMetric.class);
        when(metric.getMemberId()).thenReturn(memberId);
        when(metric.getMetCount()).thenReturn(met);
        when(metric.getSampleCount()).thenReturn(samples);
        return metric;
    }

    private ChatMessageRepository.WorkerResponseMetric responseMetric(Long memberId, double seconds, long samples) {
        ChatMessageRepository.WorkerResponseMetric metric = mock(ChatMessageRepository.WorkerResponseMetric.class);
        when(metric.getMemberId()).thenReturn(memberId);
        when(metric.getAverageResponseSeconds()).thenReturn(seconds);
        when(metric.getSampleCount()).thenReturn(samples);
        return metric;
    }
}
