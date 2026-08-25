package org.example._nd_project;

import org.example._nd_project.chat.ChatMessage;
import org.example._nd_project.chat.ChatMessageRepository;
import org.example._nd_project.chat.ChatRoom;
import org.example._nd_project.chat.ChatRoomRepository;
import org.example._nd_project.chat.ChatService;
import org.example._nd_project.task.Task;
import org.example._nd_project.task.TaskCategory;
import org.example._nd_project.task.TaskStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock ChatRoomRepository chatRoomRepository;
    @Mock ChatMessageRepository chatMessageRepository;
    @Mock org.example._nd_project.member.MemberRepository memberRepository;
    @Mock TaskStorageService taskStorageService;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(chatRoomRepository, chatMessageRepository, memberRepository, taskStorageService);
    }

    @Test
    void createsOneRoomForMatchedTask() {
        Task task = Task.create(1L, "긴급 업무", "설명", TaskCategory.DEVELOPMENT, new String[0], 60,
                Instant.now().plusSeconds(3600), "완료 기준", null);
        ReflectionTestUtils.setField(task, "id", 10L);
        task.assignWorker(2L, Instant.now());
        when(chatRoomRepository.findByTaskId(10L)).thenReturn(Optional.empty());
        when(chatRoomRepository.saveAndFlush(any(ChatRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatRoom room = chatService.ensureRoomForTask(task);

        assertEquals(10L, room.getTaskId());
        assertEquals(1L, room.getRequesterMemberId());
        assertEquals(2L, room.getWorkerMemberId());
    }

    @Test
    void outsiderCannotSendMessageOrUploadFile() {
        ChatRoom room = roomWithId(1L, 10L, 1L, 2L);
        when(chatRoomRepository.findById(1L)).thenReturn(Optional.of(room));
        MockMultipartFile file = new MockMultipartFile("attachment", "proof.png", "image/png", new byte[]{1});

        assertThrows(ResponseStatusException.class,
                () -> chatService.sendMessage(1L, 999L, "몰래 보내기", file));

        verify(taskStorageService, never()).uploadChatAttachment(any(), any());
    }

    @Test
    void participantCanSendMessageWithPrivateAttachment() {
        ChatRoom room = roomWithId(1L, 10L, 1L, 2L);
        when(chatRoomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(taskStorageService.uploadChatAttachment(any(), any()))
                .thenReturn(new TaskStorageService.StoredObject("chats/1/private.png", "image/png"));
        MockMultipartFile file = new MockMultipartFile("attachment", "proof.png", "image/png", new byte[]{1, 2});

        chatService.sendMessage(1L, 1L, "확인해주세요", file);

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(captor.capture());
        assertEquals("확인해주세요", captor.getValue().getContent());
        assertEquals("chats/1/private.png", captor.getValue().getAttachmentObjectPath());
        assertEquals("확인해주세요", room.getLastMessagePreview());
    }

    @Test
    void sentMessageIsUnreadUntilOtherMemberOpensRoom() {
        ChatRoom room = roomWithId(1L, 10L, 1L, 2L);
        ChatMessage message = ChatMessage.create(1L, 1L, "확인해주세요", null, null, null, null);
        ReflectionTestUtils.setField(message, "id", 100L);
        when(chatRoomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(chatMessageRepository.findTop100ByRoomIdOrderBySentAtDescIdDesc(1L))
                .thenReturn(List.of(message));

        var beforeRead = chatService.openRoom(1L, 1L);
        assertFalse(beforeRead.messages().get(0).read());

        ReflectionTestUtils.setField(message, "readAt", Instant.now());
        var afterRead = chatService.openRoom(1L, 1L);
        assertTrue(afterRead.messages().get(0).read());
    }

    private ChatRoom roomWithId(Long roomId, Long taskId, Long requesterId, Long workerId) {
        ChatRoom room = ChatRoom.create(taskId, requesterId, workerId, "업무 제목");
        ReflectionTestUtils.setField(room, "id", roomId);
        return room;
    }
}
