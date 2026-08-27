package org.example._nd_project.volunteer;

import org.example._nd_project.chat.ChatMessageRepository;
import org.example._nd_project.member.Member;
import org.example._nd_project.member.MemberRepository;
import org.example._nd_project.member.MemberStatus;
import org.example._nd_project.submission.ReviewRepository;
import org.example._nd_project.task.Task;
import org.example._nd_project.task.TaskRepository;
import org.example._nd_project.task.TaskStatus;
import org.example._nd_project.volunteer.ai.AiWorkerRecommendationItem;
import org.example._nd_project.volunteer.ai.AiWorkerRecommendationReport;
import org.example._nd_project.volunteer.ai.WorkerRecommendationAiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WorkerRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(WorkerRecommendationService.class);
    private static final int MAX_AI_CANDIDATES = 10;
    private static final List<TaskStatus> ACTIVE_WORK_STATUSES = List.of(
            TaskStatus.MATCHED, TaskStatus.IN_PROGRESS, TaskStatus.SUBMITTED, TaskStatus.DISPUTED
    );

    private final VolunteerRepository volunteerRepository;
    private final TaskRepository taskRepository;
    private final MemberRepository memberRepository;
    private final ReviewRepository reviewRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final WorkerRecommendationAiClient aiClient;

    public WorkerRecommendationService(
            VolunteerRepository volunteerRepository,
            TaskRepository taskRepository,
            MemberRepository memberRepository,
            ReviewRepository reviewRepository,
            ChatMessageRepository chatMessageRepository,
            WorkerRecommendationAiClient aiClient
    ) {
        this.volunteerRepository = volunteerRepository;
        this.taskRepository = taskRepository;
        this.memberRepository = memberRepository;
        this.reviewRepository = reviewRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.aiClient = aiClient;
    }

    public List<WorkerRecommendationView> recommend(Long taskId, Long requesterId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "업무를 찾을 수 없습니다."));
        if (!task.isRequester(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "의뢰인만 작업자 추천을 요청할 수 있습니다.");
        }
        if (task.getStatus() != TaskStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "모집 중인 업무에서만 추천을 받을 수 있습니다.");
        }

        List<Volunteer> applications = volunteerRepository
                .findByTaskIdAndStatusOrderByCreatedAtAsc(taskId, VolunteerStatus.APPLIED);
        if (applications.isEmpty()) {
            return List.of();
        }

        List<Long> memberIds = applications.stream().map(Volunteer::getMemberId).distinct().toList();
        Map<Long, Member> members = memberRepository.findAllById(memberIds).stream()
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .collect(Collectors.toMap(Member::getId, Function.identity()));
        if (members.isEmpty()) {
            return List.of();
        }

        List<Long> eligibleIds = memberIds.stream().filter(members::containsKey).toList();
        Map<Long, Long> categoryCounts = countMap(
                taskRepository.countCompletedByWorkersAndCategory(eligibleIds, task.getCategory())
        );
        Map<Long, Long> activeCounts = countMap(
                taskRepository.countActiveByWorkers(eligibleIds, ACTIVE_WORK_STATUSES)
        );
        Map<Long, ReviewRepository.DeadlineMetric> deadlineMetrics = reviewRepository
                .findDeadlineMetrics(eligibleIds).stream()
                .collect(Collectors.toMap(ReviewRepository.DeadlineMetric::getMemberId, Function.identity()));
        Map<Long, ChatMessageRepository.WorkerResponseMetric> responseMetrics = chatMessageRepository
                .findWorkerResponseMetrics(eligibleIds).stream()
                .collect(Collectors.toMap(ChatMessageRepository.WorkerResponseMetric::getMemberId, Function.identity()));

        List<Candidate> ranked = applications.stream()
                .filter(application -> members.containsKey(application.getMemberId()))
                .map(application -> candidate(
                        task,
                        application,
                        members.get(application.getMemberId()),
                        categoryCounts.getOrDefault(application.getMemberId(), 0L),
                        activeCounts.getOrDefault(application.getMemberId(), 0L),
                        deadlineMetrics.get(application.getMemberId()),
                        responseMetrics.get(application.getMemberId())
                ))
                .sorted(Comparator.comparingInt(Candidate::score).reversed()
                        .thenComparing(Candidate::appliedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(candidate -> candidate.volunteer().getId()))
                .toList();

        Map<String, AiWorkerRecommendationItem> aiAnalysis = analyzeWithAi(task, ranked);
        List<WorkerRecommendationView> result = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            Candidate candidate = ranked.get(index);
            AiWorkerRecommendationItem aiItem = aiAnalysis.get(candidate.key());
            result.add(toView(index + 1, candidate, aiItem));
        }
        return List.copyOf(result);
    }

    private Candidate candidate(
            Task task,
            Volunteer volunteer,
            Member member,
            long categoryCompletedCount,
            long activeTaskCount,
            ReviewRepository.DeadlineMetric deadlineMetric,
            ChatMessageRepository.WorkerResponseMetric responseMetric
    ) {
        SkillMatch skillMatch = skillMatch(task.getRequiredSkillTags(), member.getSkillTags());
        int reviewCount = member.getReviewCount();
        double rating = reviewCount == 0 ? 0.0 : (double) member.getRatingSum() / reviewCount;
        double adjustedRating = (member.getRatingSum() + 10.5) / (reviewCount + 3.0);
        long deadlineSamples = deadlineMetric == null ? 0 : deadlineMetric.getSampleCount();
        double deadlineRate = deadlineSamples == 0
                ? 0.5
                : (double) deadlineMetric.getMetCount() / deadlineSamples;
        long responseSamples = responseMetric == null ? 0 : responseMetric.getSampleCount();
        double responseSeconds = responseMetric == null || responseMetric.getAverageResponseSeconds() == null
                ? -1
                : responseMetric.getAverageResponseSeconds();

        double score = skillMatch.ratio() * 35.0
                + Math.min(categoryCompletedCount, 5) / 5.0 * 20.0
                + adjustedRating / 5.0 * 15.0
                + deadlineRate * 10.0
                + responseScore(responseSeconds, responseSamples)
                + workloadScore(activeTaskCount)
                + Math.min(member.getCompletedTaskCount(), 10) / 10.0 * 5.0;

        return new Candidate(
                "C" + volunteer.getId(), volunteer, member, clampScore(score), skillMatch,
                Math.toIntExact(categoryCompletedCount), rating, reviewCount,
                (int) Math.round(deadlineRate * 100), Math.toIntExact(deadlineSamples),
                responseSeconds, Math.toIntExact(responseSamples), Math.toIntExact(activeTaskCount)
        );
    }

    private Map<String, AiWorkerRecommendationItem> analyzeWithAi(Task task, List<Candidate> ranked) {
        List<Candidate> candidatesForAi = ranked.stream().limit(MAX_AI_CANDIDATES).toList();
        if (candidatesForAi.isEmpty()) {
            return Map.of();
        }
        try {
            AiWorkerRecommendationReport report = aiClient.analyze(evidence(task, candidatesForAi));
            if (report == null || report.candidates() == null) {
                return Map.of();
            }
            Set<String> allowedKeys = candidatesForAi.stream().map(Candidate::key).collect(Collectors.toSet());
            Map<String, AiWorkerRecommendationItem> result = new HashMap<>();
            for (AiWorkerRecommendationItem item : report.candidates()) {
                if (item == null || !allowedKeys.contains(item.candidateKey()) || result.containsKey(item.candidateKey())) {
                    continue;
                }
                result.put(item.candidateKey(), normalizeAiItem(item));
            }
            return Map.copyOf(result);
        } catch (RuntimeException exception) {
            log.warn("AI worker recommendation explanation failed: {}", exception.getClass().getSimpleName());
            return Map.of();
        }
    }

    private String evidence(Task task, List<Candidate> candidates) {
        StringBuilder evidence = new StringBuilder()
                .append("업무 카테고리: ").append(task.getCategory().name()).append('\n')
                .append("필요 기술: ").append(safeSkills(task.getRequiredSkillTags())).append('\n');
        for (Candidate candidate : candidates) {
            evidence.append("- candidateKey=").append(candidate.key())
                    .append(", 서버점수=").append(candidate.score())
                    .append(", 기술일치율=").append(candidate.skillMatch().percent()).append('%')
                    .append(", 일치기술=").append(candidate.skillMatch().matched())
                    .append(", 동일카테고리완료=").append(candidate.categoryCompletedCount())
                    .append(", 평점=").append(String.format(Locale.ROOT, "%.1f", candidate.rating()))
                    .append(", 리뷰표본=").append(candidate.reviewCount())
                    .append(", 기한준수율=").append(candidate.deadlinePercent()).append('%')
                    .append(", 기한표본=").append(candidate.deadlineSamples())
                    .append(", 평균응답초=").append(candidate.responseSamples() == 0 ? "표본없음" : Math.round(candidate.responseSeconds()))
                    .append(", 응답표본=").append(candidate.responseSamples())
                    .append(", 현재작업수=").append(candidate.activeTaskCount())
                    .append('\n');
        }
        return evidence.toString();
    }

    private WorkerRecommendationView toView(
            int rank, Candidate candidate, AiWorkerRecommendationItem aiItem
    ) {
        boolean aiEnhanced = aiItem != null;
        String summary = aiEnhanced ? aiItem.summary() : fallbackSummary(candidate);
        List<String> strengths = aiEnhanced ? aiItem.strengths() : fallbackStrengths(candidate);
        List<String> cautions = aiEnhanced ? aiItem.cautions() : fallbackCautions(candidate);
        return new WorkerRecommendationView(
                rank,
                candidate.volunteer().getId(),
                candidate.member().getId(),
                candidate.member().getNickname(),
                candidate.score(),
                candidate.skillMatch().percent(),
                candidate.skillMatch().matched(),
                candidate.categoryCompletedCount(),
                candidate.rating(),
                candidate.reviewCount(),
                candidate.deadlinePercent(),
                candidate.deadlineSamples(),
                responseLabel(candidate.responseSeconds(), candidate.responseSamples()),
                candidate.responseSamples(),
                candidate.activeTaskCount(),
                summary,
                strengths,
                cautions,
                aiEnhanced
        );
    }

    private String fallbackSummary(Candidate candidate) {
        if (candidate.skillMatch().percent() >= 70) {
            return "필요 기술과의 일치도가 높고 검증된 이력을 함께 고려한 추천입니다.";
        }
        if (candidate.categoryCompletedCount() > 0) {
            return "동일 카테고리 완료 경험을 중심으로 추천한 지원자입니다.";
        }
        return "현재 확인 가능한 프로필과 활동 데이터를 기준으로 산정한 추천입니다.";
    }

    private List<String> fallbackStrengths(Candidate candidate) {
        List<String> strengths = new ArrayList<>();
        if (!candidate.skillMatch().matched().isEmpty()) {
            strengths.add("필요 기술 일치: " + String.join(", ", candidate.skillMatch().matched()));
        }
        if (candidate.categoryCompletedCount() > 0) {
            strengths.add("동일 카테고리 완료 " + candidate.categoryCompletedCount() + "건");
        }
        if (candidate.reviewCount() > 0) {
            strengths.add(String.format(Locale.KOREA, "평점 %.1f (%d건)", candidate.rating(), candidate.reviewCount()));
        }
        return strengths.isEmpty() ? List.of("지원 프로필 데이터 확인 가능") : List.copyOf(strengths.subList(0, Math.min(3, strengths.size())));
    }

    private List<String> fallbackCautions(Candidate candidate) {
        List<String> cautions = new ArrayList<>();
        if (candidate.reviewCount() == 0) cautions.add("리뷰 표본 없음");
        if (candidate.responseSamples() == 0) cautions.add("응답 속도 표본 없음");
        if (candidate.activeTaskCount() > 0) cautions.add("현재 작업 " + candidate.activeTaskCount() + "건 진행 중");
        return List.copyOf(cautions.subList(0, Math.min(3, cautions.size())));
    }

    private AiWorkerRecommendationItem normalizeAiItem(AiWorkerRecommendationItem item) {
        return new AiWorkerRecommendationItem(
                item.candidateKey(),
                safeText(item.summary(), 100, "서버 지표를 바탕으로 분석한 추천입니다."),
                safeList(item.strengths()),
                safeList(item.cautions())
        );
    }

    private List<String> safeList(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> safeText(value, 60, ""))
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(3)
                .toList();
    }

    private String safeText(String value, int maxLength, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private Map<Long, Long> countMap(Collection<TaskRepository.WorkerCountMetric> metrics) {
        Map<Long, Long> result = new LinkedHashMap<>();
        for (TaskRepository.WorkerCountMetric metric : metrics) {
            result.put(metric.getMemberId(), metric.getCount());
        }
        return result;
    }

    private SkillMatch skillMatch(String[] requiredSkills, String[] memberSkills) {
        List<String> required = canonicalSkills(requiredSkills);
        List<String> possessed = canonicalSkills(memberSkills);
        if (required.isEmpty()) {
            return new SkillMatch(0.5, 50, List.of());
        }
        List<String> matched = new ArrayList<>();
        for (String requiredSkill : required) {
            if (possessed.stream().anyMatch(possessedSkill -> skillEquals(requiredSkill, possessedSkill))) {
                matched.add(requiredSkill);
            }
        }
        double ratio = (double) matched.size() / required.size();
        return new SkillMatch(ratio, (int) Math.round(ratio * 100), List.copyOf(matched));
    }

    private List<String> canonicalSkills(String[] skills) {
        if (skills == null) return List.of();
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(skills)
                .filter(skill -> skill != null && !skill.isBlank())
                .map(this::canonicalSkill)
                .filter(skill -> !skill.isBlank())
                .forEach(result::add);
        return List.copyOf(result);
    }

    private String canonicalSkill(String skill) {
        String normalized = skill.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9가-힣]", "");
        return switch (normalized) {
            case "springboot", "springframework" -> "spring";
            case "amazonwebservices" -> "aws";
            case "postgresql" -> "postgres";
            case "javascript" -> "js";
            case "typescript" -> "ts";
            case "reactjs" -> "react";
            case "nodejs" -> "node";
            default -> normalized;
        };
    }

    private boolean skillEquals(String required, String possessed) {
        if (required.equals(possessed)) return true;
        return required.length() >= 3 && possessed.length() >= 3
                && (required.contains(possessed) || possessed.contains(required));
    }

    private String safeSkills(String[] skills) {
        return String.join(", ", canonicalSkills(skills));
    }

    private double responseScore(double seconds, long samples) {
        if (samples == 0 || seconds < 0) return 4.0;
        if (seconds <= Duration.ofMinutes(5).toSeconds()) return 10.0;
        if (seconds <= Duration.ofMinutes(15).toSeconds()) return 8.0;
        if (seconds <= Duration.ofHours(1).toSeconds()) return 6.0;
        if (seconds <= Duration.ofHours(3).toSeconds()) return 4.0;
        if (seconds <= Duration.ofHours(12).toSeconds()) return 2.0;
        return 0.0;
    }

    private double workloadScore(long activeTaskCount) {
        if (activeTaskCount == 0) return 5.0;
        if (activeTaskCount == 1) return 2.5;
        return 0.0;
    }

    private int clampScore(double score) {
        return (int) Math.round(Math.max(0, Math.min(100, score)));
    }

    private String responseLabel(double seconds, int samples) {
        if (samples == 0 || seconds < 0) return "데이터 없음";
        long roundedSeconds = Math.round(seconds);
        if (roundedSeconds < 60) return roundedSeconds + "초";
        long minutes = roundedSeconds / 60;
        if (minutes < 60) return minutes + "분";
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        return remainingMinutes == 0 ? hours + "시간" : hours + "시간 " + remainingMinutes + "분";
    }

    private record SkillMatch(double ratio, int percent, List<String> matched) {}

    private record Candidate(
            String key,
            Volunteer volunteer,
            Member member,
            int score,
            SkillMatch skillMatch,
            int categoryCompletedCount,
            double rating,
            int reviewCount,
            int deadlinePercent,
            int deadlineSamples,
            double responseSeconds,
            int responseSamples,
            int activeTaskCount
    ) {
        private Instant appliedAt() {
            return volunteer.getCreatedAt();
        }
    }
}
