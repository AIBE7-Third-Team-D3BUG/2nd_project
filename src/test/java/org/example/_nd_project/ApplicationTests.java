package org.example._nd_project;

import org.example._nd_project.member.MemberRepository;
import org.example._nd_project.member.TimeAccountRepository;
import org.example._nd_project.member.TimeTransactionRepository;
import org.example._nd_project.submission.DisputeRepository;
import org.example._nd_project.submission.SubmissionRepository;
import org.example._nd_project.task.TaskRepository;
import org.example._nd_project.volunteer.VolunteerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("web")
class ApplicationTests {

    @MockitoBean MemberRepository memberRepository;
    @MockitoBean TimeAccountRepository timeAccountRepository;
    @MockitoBean TimeTransactionRepository timeTransactionRepository;
    @MockitoBean TaskRepository taskRepository;
    @MockitoBean SubmissionRepository submissionRepository;
    @MockitoBean DisputeRepository disputeRepository;
    @MockitoBean VolunteerRepository volunteerRepository;

    @Test
    void contextLoads() {
    }

}
