package org.example._nd_project;

import org.example._nd_project.chat.ChatService;
import org.example._nd_project.member.Member;
import org.example._nd_project.member.MemberRepository;
import org.example._nd_project.task.Task;
import org.example._nd_project.task.TaskCategory;
import org.example._nd_project.task.TaskRepository;
import org.example._nd_project.volunteer.Volunteer;
import org.example._nd_project.volunteer.VolunteerRepository;
import org.example._nd_project.volunteer.VolunteerService;
import org.example._nd_project.volunteer.VolunteerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VolunteerServiceTest {

    @Mock VolunteerRepository volunteerRepository;
    @Mock TaskRepository taskRepository;
    @Mock MemberRepository memberRepository;
    @Mock ChatService chatService;

    private VolunteerService volunteerService;

    @BeforeEach
    void setUp() {
        volunteerService = new VolunteerService(volunteerRepository, taskRepository, memberRepository, chatService);
    }

    @Test
    void cannotApplyToOwnTask() {
        Task task = Task.create(1L, "title", "desc", TaskCategory.DEVELOPMENT, new String[0], 60,
                Instant.now().plusSeconds(3600), "deliverable", null);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

        assertThrows(ResponseStatusException.class, () ->
                volunteerService.apply(10L, 1L, "msg"));
    }

    @Test
    void cannotApplyTwice() {
        Task task = Task.create(1L, "title", "desc", TaskCategory.DEVELOPMENT, new String[0], 60,
                Instant.now().plusSeconds(3600), "deliverable", null);
        Volunteer existing = Volunteer.create(10L, 2L, "msg");
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(volunteerRepository.findByTaskIdAndMemberId(10L, 2L)).thenReturn(Optional.of(existing));

        assertThrows(ResponseStatusException.class, () ->
                volunteerService.apply(10L, 2L, "msg"));
    }

    @Test
    void applySucceeds() {
        Task task = Task.create(1L, "title", "desc", TaskCategory.DEVELOPMENT, new String[0], 60,
                Instant.now().plusSeconds(3600), "deliverable", null);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(volunteerRepository.findByTaskIdAndMemberId(10L, 2L)).thenReturn(Optional.empty());
        when(volunteerRepository.save(any(Volunteer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Volunteer result = volunteerService.apply(10L, 2L, "msg");

        assertNotNull(result);
        assertEquals(10L, result.getTaskId());
        assertEquals(2L, result.getMemberId());
        assertEquals("msg", result.getMessage());
        assertEquals(VolunteerStatus.APPLIED, result.getStatus());
    }

    @Test
    void nonOwnerCannotSelectVolunteer() {
        Task task = Task.create(1L, "title", "desc", TaskCategory.DEVELOPMENT, new String[0], 60,
                Instant.now().plusSeconds(3600), "deliverable", null);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

        assertThrows(ResponseStatusException.class, () ->
                volunteerService.selectVolunteer(10L, 999L, 50L));
    }

    @Test
    void selectingVolunteerCreatesChatRoom() {
        Task task = Task.create(1L, "title", "desc", TaskCategory.DEVELOPMENT, new String[0], 60,
                Instant.now().plusSeconds(3600), "deliverable", null);
        org.springframework.test.util.ReflectionTestUtils.setField(task, "id", 10L);
        Volunteer volunteer = Volunteer.create(10L, 2L, "msg");
        org.springframework.test.util.ReflectionTestUtils.setField(volunteer, "id", 50L);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(volunteerRepository.findByIdAndTaskId(50L, 10L)).thenReturn(Optional.of(volunteer));
        when(volunteerRepository.findByTaskIdAndStatusNotOrderByCreatedAtAsc(10L, VolunteerStatus.CANCELLED))
                .thenReturn(java.util.List.of(volunteer));

        volunteerService.selectVolunteer(10L, 1L, 50L);

        verify(chatService).ensureRoomForTask(task);
        assertEquals(org.example._nd_project.task.TaskStatus.MATCHED, task.getStatus());
    }

    @Test
    void cancelApplicationSucceeds() {
        Volunteer volunteer = Volunteer.create(10L, 2L, "msg");
        when(volunteerRepository.findByTaskIdAndMemberId(10L, 2L)).thenReturn(Optional.of(volunteer));

        volunteerService.cancelApplication(10L, 2L);

        org.mockito.Mockito.verify(volunteerRepository).delete(volunteer);
    }

    @Test
    void cancelAcceptedApplicationReopensTask() {
        Task task = Task.create(1L, "title", "desc", TaskCategory.DEVELOPMENT, new String[0], 60,
                Instant.now().plusSeconds(3600), "deliverable", null);
        task.assignWorker(2L, Instant.now());

        Volunteer volunteer = Volunteer.create(10L, 2L, "msg");
        volunteer.accept();

        when(volunteerRepository.findByTaskIdAndMemberId(10L, 2L)).thenReturn(Optional.of(volunteer));
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(volunteerRepository.findByTaskIdAndStatusNotOrderByCreatedAtAsc(10L, VolunteerStatus.CANCELLED))
                .thenReturn(java.util.List.of(volunteer));

        volunteerService.cancelApplication(10L, 2L);

        org.mockito.Mockito.verify(volunteerRepository).delete(volunteer);
        assertEquals(org.example._nd_project.task.TaskStatus.OPEN, task.getStatus());
    }

    @Test
    void unselectVolunteerSucceeds() {
        Task task = Task.create(1L, "title", "desc", TaskCategory.DEVELOPMENT, new String[0], 60,
                Instant.now().plusSeconds(3600), "deliverable", null);
        task.assignWorker(2L, Instant.now());

        Volunteer volunteer = Volunteer.create(10L, 2L, "msg");
        volunteer.accept();

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(volunteerRepository.findByIdAndTaskId(50L, 10L)).thenReturn(Optional.of(volunteer));
        when(volunteerRepository.findByTaskIdAndStatusNotOrderByCreatedAtAsc(10L, VolunteerStatus.CANCELLED))
                .thenReturn(java.util.List.of(volunteer));

        volunteerService.unselectVolunteer(10L, 1L, 50L);

        assertEquals(VolunteerStatus.APPLIED, volunteer.getStatus());
        assertEquals(org.example._nd_project.task.TaskStatus.OPEN, task.getStatus());
    }

    @Test
    void cannotCancelNonExistingApplication() {
        when(volunteerRepository.findByTaskIdAndMemberId(10L, 2L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                volunteerService.cancelApplication(10L, 2L));
    }

    @Test
    void findAppliedTasksOnlyReturnsAppliedApplications() {
        Volunteer applied = Volunteer.create(10L, 2L, "msg");
        Task task = Task.create(1L, "title", "desc", TaskCategory.DEVELOPMENT, new String[0], 60,
                Instant.now().plusSeconds(3600), "deliverable", null);
        org.springframework.test.util.ReflectionTestUtils.setField(task, "id", 10L);

        when(volunteerRepository.findByMemberIdAndStatusOrderByCreatedAtDesc(2L, VolunteerStatus.APPLIED))
                .thenReturn(java.util.List.of(applied));
        when(taskRepository.findAllById(java.util.List.of(10L))).thenReturn(java.util.List.of(task));

        java.util.List<org.example._nd_project.volunteer.AppliedTaskItem> result = volunteerService.findAppliedTasks(2L);

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).taskId());
    }
}
