package org.example._nd_project;

import org.example._nd_project.member.MemberRepository;
import org.example._nd_project.member.TimeAccountRepository;
import org.example._nd_project.member.TimeTransactionRepository;
import org.example._nd_project.task.TaskRepository;
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
    @MockitoBean org.example._nd_project.volunteer.VolunteerRepository volunteerRepository;

    @Test
    void contextLoads() {
    }

}
