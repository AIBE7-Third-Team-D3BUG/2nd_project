package org.example._nd_project.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TaskStorageService {

    private static final long MAX_FILE_SIZE = 6L * 1024 * 1024;
    private static final Set<String> ALLOWED_APPLICATION_TYPES = Set.of(
            "application/pdf",
            "application/zip",
            "application/x-zip-compressed",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/x-hwp",
            "application/haansofthwp"
    );
    private static final Map<String, String> EXTENSION_CONTENT_TYPES = Map.ofEntries(
            Map.entry("txt", "text/plain"),
            Map.entry("log", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("json", "text/plain"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("webp", "image/webp"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("zip", "application/zip"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("hwp", "application/x-hwp")
    );

    private final String supabaseUrl;
    private final String serviceRoleKey;
    private final String bucket;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public TaskStorageService(@Value("${SUPABASE_URL:}") String supabaseUrl,
                              @Value("${SUPABASE_SERVICE_ROLE_KEY:}") String serviceRoleKey,
                              @Value("${SUPABASE_STORAGE_BUCKET:task-attachments}") String bucket) {
        this.supabaseUrl = supabaseUrl.replaceAll("/+$", "");
        this.serviceRoleKey = serviceRoleKey;
        this.bucket = bucket;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String upload(Long taskId, MultipartFile file) {
        validateConfiguration();
        validateFile(file);

        String extension = extensionOf(file.getOriginalFilename());
        String contentType = resolveContentType(file.getContentType(), extension);
        String objectPath = "tasks/" + taskId + "/" + UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);

        try {
            HttpRequest request = authorizedRequest(
                    URI.create(supabaseUrl + "/storage/v1/object/" + bucket + "/" + objectPath)
            )
                    .header("Content-Type", contentType)
                    .header("x-upsert", "false")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TaskStorageException("첨부 파일 업로드에 실패했습니다. 잠시 후 다시 시도해주세요.");
            }
            return objectPath;
        } catch (IOException exception) {
            throw new TaskStorageException("첨부 파일을 읽거나 업로드하지 못했습니다.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TaskStorageException("첨부 파일 업로드가 중단되었습니다.", exception);
        }
    }

    public String uploadProfileImage(Long memberId, MultipartFile file) {
        validateConfiguration();
        validateProfileImage(file);

        String extension = extensionOf(file.getOriginalFilename());
        String contentType = resolveContentType(file.getContentType(), extension);
        String objectPath = "profiles/" + memberId + "/" + UUID.randomUUID() + "." + extension;

        try {
            HttpRequest request = authorizedRequest(
                    URI.create(supabaseUrl + "/storage/v1/object/" + bucket + "/" + objectPath)
            )
                    .header("Content-Type", contentType)
                    .header("x-upsert", "false")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TaskStorageException("프로필 사진 업로드에 실패했습니다. 잠시 후 다시 시도해주세요.");
            }
            return objectPath;
        } catch (IOException exception) {
            throw new TaskStorageException("프로필 사진을 읽거나 업로드하지 못했습니다.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TaskStorageException("프로필 사진 업로드가 중단되었습니다.", exception);
        }
    }

    public URI createSignedDownloadUrl(String objectPath) {
        validateConfiguration();
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of("expiresIn", 300));
            HttpRequest request = authorizedRequest(
                    URI.create(supabaseUrl + "/storage/v1/object/sign/" + bucket + "/" + objectPath)
            )
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TaskStorageException("첨부 파일 다운로드 주소를 만들지 못했습니다.");
            }
            JsonNode body = objectMapper.readTree(response.body());
            String signedUrl = body.path("signedURL").asText();
            if (!StringUtils.hasText(signedUrl)) {
                throw new TaskStorageException("첨부 파일 다운로드 주소가 비어 있습니다.");
            }
            URI parsed = URI.create(signedUrl);
            return parsed.isAbsolute() ? parsed : URI.create(supabaseUrl + "/storage/v1" + signedUrl);
        } catch (IOException exception) {
            throw new TaskStorageException("첨부 파일 다운로드 응답을 처리하지 못했습니다.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TaskStorageException("첨부 파일 다운로드 요청이 중단되었습니다.", exception);
        }
    }

    public void deleteQuietly(String objectPath) {
        if (!StringUtils.hasText(objectPath)) {
            return;
        }
        try {
            HttpRequest request = authorizedRequest(
                    URI.create(supabaseUrl + "/storage/v1/object/" + bucket + "/" + objectPath)
            ).DELETE().build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (IOException ignored) {
            // DB 롤백을 방해하지 않도록 정리 실패는 무시한다.
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private HttpRequest.Builder authorizedRequest(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("apikey", serviceRoleKey)
                .header("Authorization", "Bearer " + serviceRoleKey);
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(supabaseUrl)
                || !StringUtils.hasText(serviceRoleKey)
                || !StringUtils.hasText(bucket)) {
            throw new TaskStorageException("Supabase Storage 환경변수가 설정되지 않았습니다.");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new TaskStorageException("업로드할 파일이 비어 있습니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new TaskStorageException("첨부 파일은 6MB 이하만 업로드할 수 있습니다.");
        }
        String extension = extensionOf(file.getOriginalFilename());
        String contentType = resolveContentType(file.getContentType(), extension);
        if (!(contentType.startsWith("image/")
                || contentType.startsWith("text/")
                || ALLOWED_APPLICATION_TYPES.contains(contentType))) {
            throw new TaskStorageException("지원하지 않는 첨부 파일 형식입니다.");
        }
    }

    private void validateProfileImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new TaskStorageException("업로드할 프로필 사진이 비어 있습니다.");
        }
        if (file.getSize() > 3L * 1024 * 1024) {
            throw new TaskStorageException("프로필 사진은 3MB 이하만 업로드할 수 있습니다.");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!Set.of("jpg", "jpeg", "png", "webp").contains(extension)) {
            throw new TaskStorageException("프로필 사진은 JPG, PNG, WEBP 형식만 사용할 수 있습니다.");
        }
        if (!resolveContentType(file.getContentType(), extension).startsWith("image/")) {
            throw new TaskStorageException("올바른 이미지 파일을 선택해주세요.");
        }
    }

    private String resolveContentType(String suppliedContentType, String extension) {
        String inferred = EXTENSION_CONTENT_TYPES.get(extension);
        if (inferred != null) {
            return inferred;
        }
        if (suppliedContentType == null) {
            return "application/octet-stream";
        }
        return suppliedContentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private String extensionOf(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return extension.matches("[a-z0-9]{1,10}") ? extension : "";
    }
}
