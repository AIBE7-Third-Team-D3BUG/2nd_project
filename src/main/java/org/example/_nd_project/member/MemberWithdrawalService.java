package org.example._nd_project.member;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.example._nd_project.task.TaskStorageService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@Profile("db")
public class MemberWithdrawalService {

    private final MemberRepository memberRepository;
    private final EntityManager entityManager;
    private final PasswordEncoder passwordEncoder;
    private final TaskStorageService taskStorageService;

    public MemberWithdrawalService(MemberRepository memberRepository,
                                   EntityManager entityManager,
                                   PasswordEncoder passwordEncoder,
                                   TaskStorageService taskStorageService) {
        this.memberRepository = memberRepository;
        this.entityManager = entityManager;
        this.passwordEncoder = passwordEncoder;
        this.taskStorageService = taskStorageService;
    }

    /** Deletes the member's application data from the configured Supabase PostgreSQL database. */
    @Transactional
    public void withdraw(Long memberId, String password) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));
        if (member.getRole() == MemberRole.ADMIN) {
            throw new IllegalStateException("관리자 계정은 이 화면에서 탈퇴할 수 없습니다.");
        }
        if (password == null || !passwordEncoder.matches(password, member.getPasswordHash())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        Set<String> storageObjects = new LinkedHashSet<>();
        addAll(storageObjects, "select profile_image_url from members where id = ?", memberId);
        addAll(storageObjects, "select reference_file_url from tasks where requester_id = ? or worker_id = ?", memberId, memberId);
        addAll(storageObjects, """
                select result_file_url from submissions where worker_id = ? or task_id in
                (select id from tasks where requester_id = ? or worker_id = ?)
                """, memberId, memberId, memberId);
        addAll(storageObjects, """
                select evidence_url from disputes where opened_by_member_id = ? or task_id in
                (select id from tasks where requester_id = ? or worker_id = ?)
                """, memberId, memberId, memberId);
        addAll(storageObjects, """
                select attachment_object_path from chat_messages where room_id in
                (select id from chat_rooms where requester_member_id = ? or worker_member_id = ?)
                """, memberId, memberId);

        // Time transactions are append-only outside this explicitly scoped withdrawal transaction.
        entityManager.createNativeQuery("select set_config('app.member_withdrawal', 'on', true)").getSingleResult();

        execute("delete from admin_audit_logs where admin_member_id = ?", memberId);
        execute("delete from chat_rooms where requester_member_id = ? or worker_member_id = ?", memberId, memberId);
        execute("""
                delete from reviews where reviewer_id = ? or reviewee_id = ? or task_id in
                (select id from tasks where requester_id = ? or worker_id = ?)
                """, memberId, memberId, memberId, memberId);
        execute("""
                delete from disputes where opened_by_member_id = ? or task_id in
                (select id from tasks where requester_id = ? or worker_id = ?)
                """, memberId, memberId, memberId);
        execute("""
                delete from submissions where worker_id = ? or task_id in
                (select id from tasks where requester_id = ? or worker_id = ?)
                """, memberId, memberId, memberId);
        execute("""
                delete from volunteer where member_id = ? or task_id in
                (select id from tasks where requester_id = ? or worker_id = ?)
                """, memberId, memberId, memberId);
        execute("delete from time_transactions where account_member_id = ?", memberId);
        execute("delete from tasks where requester_id = ? or worker_id = ?", memberId, memberId);
        execute("delete from time_accounts where member_id = ?", memberId);
        execute("delete from members where id = ?", memberId);

        // Reviews written by the withdrawn member can affect another member's aggregate rating.
        execute("""
                update members member set review_count = summary.review_count, rating_sum = summary.rating_sum
                from (
                    select members.id as member_id, count(reviews.id)::integer as review_count,
                           coalesce(sum(reviews.rating), 0)::integer as rating_sum
                    from members left join reviews on reviews.reviewee_id = members.id
                    group by members.id
                ) summary where member.id = summary.member_id
                """);

        Set<String> committedStorageObjects = Set.copyOf(storageObjects);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                committedStorageObjects.forEach(taskStorageService::deleteQuietly);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void addAll(Set<String> target, String sql, Object... parameters) {
        List<String> results = bind(entityManager.createNativeQuery(sql), parameters).getResultList();
        results.stream().filter(value -> value != null && !value.isBlank()).forEach(target::add);
    }

    private void execute(String sql, Object... parameters) {
        bind(entityManager.createNativeQuery(sql), parameters).executeUpdate();
    }

    private Query bind(Query query, Object... parameters) {
        for (int index = 0; index < parameters.length; index++) {
            query.setParameter(index + 1, parameters[index]);
        }
        return query;
    }
}
