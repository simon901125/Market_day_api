# Notification 標記已讀 API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `POST /api/notification/{id}/isRead`, letting the currently logged-in user mark their own notification as read.

**Architecture:** A new `NotificationController` delegates to a new `markAsRead` method on the existing `Service/NotificationService.java`. That method authenticates the caller via JWT, resolves their `User` row by email (new `UserRepo.findByEmail`), loads the `Notification` via the existing JPA `NotificationRepo`, verifies ownership, and updates `isRead`/`readAt` idempotently.

**Tech Stack:** Spring Boot, Spring Data JPA, JUnit 5 + Mockito + AssertJ, Maven (`./mvnw.cmd`).

## Global Constraints

- Endpoint: `POST /api/notification/{id}/isRead` (matches this project's existing convention of using `PostMapping` for all mutating actions — see `docs/superpowers/specs/2026-07-17-notification-mark-as-read-design.md`).
- Any authenticated role may call this endpoint (not restricted to organizer/vendor); ownership is enforced by comparing the caller's user id to `Notification.user.id`.
- Already-read notifications return success without rewriting `readAt` (idempotent).
- Use the JPA `NotificationRepo` (not the JDBC-based `NotificationRepository`) for the read/update, per the approved spec.
- New failure/success message strings must get a Chinese translation entry in `ApiResponse` (`toChineseErrorMessage` / `toChineseSuccessMessage`), or they silently fall back to a generic Chinese message.
- Compile and run the full test suite successfully before any commit (`./mvnw.cmd -q test`), per project instructions.

---

### Task 1: `ApiResponse` Chinese message mappings

**Files:**
- Modify: `src/main/java/com/example/demo/dto/response/ApiResponse.java`
- Test: `src/test/java/com/example/demo/dto/response/ApiResponseTest.java`

**Interfaces:**
- Produces: `ApiResponse.fail("Notification not found")`, `ApiResponse.fail("Notification does not belong to this account")`, `ApiResponse.success("Notification marked as read", data)` — all now return the correct Chinese `message`. Later tasks call these exact English strings.

- [ ] **Step 1: Write the failing tests**

Add to `src/test/java/com/example/demo/dto/response/ApiResponseTest.java` (inside the existing `class ApiResponseTest { ... }` body, after the last `@Test` method):

```java
    @Test
    void translatesNotificationMarkAsReadMessages() {
        assertThat(ApiResponse.fail("Notification not found").getMessage())
                .isEqualTo("找不到通知");
        assertThat(ApiResponse.fail("Notification does not belong to this account").getMessage())
                .isEqualTo("此通知不屬於目前登入帳號");
        assertThat(ApiResponse.success("Notification marked as read", null).getMessage())
                .isEqualTo("通知已標記為已讀");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw.cmd -q test -Dtest=ApiResponseTest`
Expected: FAIL — `translatesNotificationMarkAsReadMessages` fails because `toChineseErrorMessage`/`toChineseSuccessMessage` don't yet recognize these strings (they currently fall back to the generic `"操作失敗"` / `"操作成功"`).

- [ ] **Step 3: Add the mappings**

In `src/main/java/com/example/demo/dto/response/ApiResponse.java`, inside `toChineseSuccessMessage`, add a new `case` right before the `default ->` line (around line 175):

```java
            case "Notification marked as read" -> "通知已標記為已讀";
            default -> isLikelyEnglish(message) ? "操作成功" : message;
```

(replace only the `default ->` line, keeping the new `case` immediately above it)

Inside `toChineseErrorMessage`, add two new `case` lines right before its `default ->` line (around line 373):

```java
            case "Notification not found" -> "找不到通知";
            case "Notification does not belong to this account" -> "此通知不屬於目前登入帳號";
            default -> isLikelyEnglish(message) ? "操作失敗" : message;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw.cmd -q test -Dtest=ApiResponseTest`
Expected: PASS (no output in quiet mode, exit code 0)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/demo/dto/response/ApiResponse.java src/test/java/com/example/demo/dto/response/ApiResponseTest.java
git commit -m "feat: 新增通知標記已讀訊息的中文對應"
```

---

### Task 2: `NotificationToggleDto` and `UserRepo.findByEmail`

**Files:**
- Create: `src/main/java/com/example/demo/dto/response/NotificationToggleDto.java`
- Modify: `src/main/java/com/example/demo/Repository/UserRepo.java`

**Interfaces:**
- Produces: `record NotificationToggleDto(Long id, boolean isRead)` and `Optional<User> UserRepo.findByEmail(String email)` — both consumed by Task 3's `NotificationService.markAsRead`.

- [ ] **Step 1: Create the DTO**

Create `src/main/java/com/example/demo/dto/response/NotificationToggleDto.java`:

```java
package com.example.demo.dto.response;

public record NotificationToggleDto(Long id, boolean isRead) {
}
```

- [ ] **Step 2: Add the derived query method to `UserRepo`**

In `src/main/java/com/example/demo/Repository/UserRepo.java`, add this method inside the `UserRepo` interface (e.g. right after the `int countByRoleAndStatus(Role role, UserStatus status);` line):

```java
    Optional<User> findByEmail(String email);
```

(`Optional` and `User` are already imported in this file.)

- [ ] **Step 3: Compile to verify there are no errors**

Run: `./mvnw.cmd -q compile`
Expected: BUILD SUCCESS (no output in quiet mode, exit code 0). There is no dedicated unit test for this step — `NotificationToggleDto` is a plain record and `findByEmail` is a Spring Data derived query, consistent with how other simple DTOs/derived queries in this codebase are left uncovered by isolated tests; both are exercised indirectly by Task 3's service tests (mocked) and manual verification.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/demo/dto/response/NotificationToggleDto.java src/main/java/com/example/demo/Repository/UserRepo.java
git commit -m "feat: 新增 NotificationToggleDto 與 UserRepo.findByEmail"
```

---

### Task 3: `NotificationService.markAsRead`

**Files:**
- Modify: `src/main/java/com/example/demo/Service/NotificationService.java`
- Modify: `src/test/java/com/example/demo/Service/NotificationServiceTest.java`

**Interfaces:**
- Consumes: `NotificationToggleDto(Long id, boolean isRead)` (Task 2), `UserRepo.findByEmail(String email): Optional<User>` (Task 2), `NotificationRepo` (`JpaRepository<Notification, Long>` — `findById`/`save` from Spring Data), `JwtService.extractTokenFromAuthorizationHeader(String): String`, `JwtService.isTokenValid(String): boolean`, `JwtService.getEmail(String): String`, `Notification.getUser(): User`, `Notification.getIsRead(): Boolean`, `Notification.setIsRead(Boolean)`, `Notification.setReadAt(LocalDateTime)`, `User.getId(): Long`.
- Produces: `ApiResponse<NotificationToggleDto> NotificationService.markAsRead(String authorizationHeader, Long id)` — consumed by Task 4's `NotificationController`.

- [ ] **Step 1: Write the failing tests**

In `src/test/java/com/example/demo/Service/NotificationServiceTest.java`, replace the import block (lines 1-22) with:

```java
package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.demo.Repository.NotificationRepo;
import com.example.demo.Repository.NotificationRepository;
import com.example.demo.Repository.UserRepo;
import com.example.demo.dto.notification.NotificationCreateCommand;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.entity.Notification;
import com.example.demo.entity.User;
import com.example.demo.enums.notification.NotificationCategory;
import com.example.demo.enums.notification.NotificationTargetType;
import com.example.demo.enums.notification.NotificationType;
```

Replace the field declarations and `setUp()` (lines 24-35) with:

```java
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationRepo notificationRepo;

    @Mock
    private UserRepo userRepo;

    @Mock
    private JwtService jwtService;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, notificationRepo, userRepo, jwtService);
    }
```

Then add these new tests at the end of the class, right before the final closing `}` of `NotificationServiceTest` (after the existing `capturedCommand()` helper method):

```java

    @Test
    void markAsReadUpdatesIsReadAndReadAtForOwner() {
        User owner = userWithId(5L);
        Notification notification = notificationOwnedBy(owner, false);
        authenticate("token", "owner@example.com", owner);
        when(notificationRepo.findById(1L)).thenReturn(Optional.of(notification));

        ApiResponse<com.example.demo.dto.response.NotificationToggleDto> response =
                notificationService.markAsRead("Bearer token", 1L);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().id()).isEqualTo(1L);
        assertThat(response.getData().isRead()).isTrue();
        assertThat(notification.getIsRead()).isTrue();
        assertThat(notification.getReadAt()).isNotNull();
        verify(notificationRepo).save(notification);
    }

    @Test
    void markAsReadOnAlreadyReadNotificationIsIdempotent() {
        User owner = userWithId(5L);
        Notification notification = notificationOwnedBy(owner, true);
        notification.setReadAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        authenticate("token", "owner@example.com", owner);
        when(notificationRepo.findById(1L)).thenReturn(Optional.of(notification));

        ApiResponse<com.example.demo.dto.response.NotificationToggleDto> response =
                notificationService.markAsRead("Bearer token", 1L);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().isRead()).isTrue();
        assertThat(notification.getReadAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
        verify(notificationRepo, never()).save(any());
    }

    @Test
    void markAsReadReturnsFailureWhenNotificationMissing() {
        authenticate("token", "owner@example.com", userWithId(5L));
        when(notificationRepo.findById(1L)).thenReturn(Optional.empty());

        ApiResponse<com.example.demo.dto.response.NotificationToggleDto> response =
                notificationService.markAsRead("Bearer token", 1L);

        assertThat(response.isSuccessStatus()).isFalse();
        verify(notificationRepo, never()).save(any());
    }

    @Test
    void markAsReadReturnsFailureWhenNotOwner() {
        User owner = userWithId(5L);
        Notification notification = notificationOwnedBy(owner, false);
        authenticate("token", "someone-else@example.com", userWithId(9L));
        when(notificationRepo.findById(1L)).thenReturn(Optional.of(notification));

        ApiResponse<com.example.demo.dto.response.NotificationToggleDto> response =
                notificationService.markAsRead("Bearer token", 1L);

        assertThat(response.isSuccessStatus()).isFalse();
        verify(notificationRepo, never()).save(any());
    }

    @Test
    void markAsReadReturnsFailureWhenTokenMissingOrInvalid() {
        when(jwtService.extractTokenFromAuthorizationHeader(null)).thenReturn(null);
        ApiResponse<com.example.demo.dto.response.NotificationToggleDto> missing =
                notificationService.markAsRead(null, 1L);
        assertThat(missing.isSuccessStatus()).isFalse();

        when(jwtService.extractTokenFromAuthorizationHeader("Bearer bad")).thenReturn("bad");
        when(jwtService.isTokenValid("bad")).thenReturn(false);
        ApiResponse<com.example.demo.dto.response.NotificationToggleDto> invalid =
                notificationService.markAsRead("Bearer bad", 1L);
        assertThat(invalid.isSuccessStatus()).isFalse();

        verify(notificationRepo, never()).findById(any());
    }

    private void authenticate(String token, String email, User user) {
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer " + token)).thenReturn(token);
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.getEmail(token)).thenReturn(email);
        when(userRepo.findByEmail(email)).thenReturn(Optional.of(user));
    }

    private User userWithId(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Notification notificationOwnedBy(User owner, boolean isRead) {
        Notification notification = new Notification();
        notification.setId(1L);
        notification.setUser(owner);
        notification.setIsRead(isRead);
        return notification;
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw.cmd -q test -Dtest=NotificationServiceTest`
Expected: COMPILE ERROR — `NotificationService` has no constructor taking 4 arguments and no `markAsRead` method yet.

- [ ] **Step 3: Implement `markAsRead` in `NotificationService`**

In `src/main/java/com/example/demo/Service/NotificationService.java`, replace the import block (lines 1-13) with:

```java
package com.example.demo.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.Repository.NotificationRepo;
import com.example.demo.Repository.NotificationRepository;
import com.example.demo.Repository.UserRepo;
import com.example.demo.dto.notification.NotificationCreateCommand;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.NotificationToggleDto;
import com.example.demo.entity.Notification;
import com.example.demo.entity.User;
import com.example.demo.enums.notification.NotificationCategory;
import com.example.demo.enums.notification.NotificationTargetType;
import com.example.demo.enums.notification.NotificationType;
```

Replace the field declaration and constructor (lines 16-24) with:

```java
@Service
public class NotificationService {

    private static final int MAX_TITLE_LENGTH = 150;

    private final NotificationRepository notificationRepository;
    private final NotificationRepo notificationRepo;
    private final UserRepo userRepo;
    private final JwtService jwtService;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationRepo notificationRepo,
            UserRepo userRepo,
            JwtService jwtService) {
        this.notificationRepository = notificationRepository;
        this.notificationRepo = notificationRepo;
        this.userRepo = userRepo;
        this.jwtService = jwtService;
    }
```

Add the following two methods right after the constructor (before the `create` method):

```java

    public ApiResponse<NotificationToggleDto> markAsRead(String authorizationHeader, Long id) {
        Map<String, Object> auth = authenticatedUser(authorizationHeader);
        if (auth.containsKey("message")) {
            return ApiResponse.fail(auth.get("message").toString());
        }
        Long userId = ((Number) auth.get("userId")).longValue();

        Notification notification = notificationRepo.findById(id).orElse(null);
        if (notification == null) {
            return ApiResponse.fail("Notification not found");
        }
        if (!notification.getUser().getId().equals(userId)) {
            return ApiResponse.fail("Notification does not belong to this account");
        }

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification.setReadAt(LocalDateTime.now());
            notificationRepo.save(notification);
        }

        return ApiResponse.success(
                "Notification marked as read",
                new NotificationToggleDto(notification.getId(), notification.getIsRead()));
    }

    private Map<String, Object> authenticatedUser(String authorizationHeader) {
        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        if (token == null || token.isBlank()) {
            return Map.of("message", "Authorization token is required");
        }
        if (!jwtService.isTokenValid(token)) {
            return Map.of("message", "Invalid or expired token");
        }
        User user = userRepo.findByEmail(jwtService.getEmail(token)).orElse(null);
        if (user == null) {
            return Map.of("message", "User not found");
        }
        return Map.of("userId", user.getId());
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw.cmd -q test -Dtest=NotificationServiceTest`
Expected: PASS (no output in quiet mode, exit code 0)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/demo/Service/NotificationService.java src/test/java/com/example/demo/Service/NotificationServiceTest.java
git commit -m "feat: 實作 NotificationService.markAsRead 標記通知已讀"
```

---

### Task 4: `NotificationController`

**Files:**
- Create: `src/main/java/com/example/demo/Controller/NotificationController.java`
- Create: `src/test/java/com/example/demo/Controller/NotificationControllerTest.java`

**Interfaces:**
- Consumes: `ApiResponse<NotificationToggleDto> NotificationService.markAsRead(String authorizationHeader, Long id)` (Task 3).
- Produces: `POST /api/notification/{id}/isRead` HTTP endpoint.

- [ ] **Step 1: Write the failing controller test**

Create `src/test/java/com/example/demo/Controller/NotificationControllerTest.java`:

```java
package com.example.demo.Controller;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.Service.NotificationService;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {
    @Mock NotificationService notificationService;
    NotificationController controller;
    static final String AUTH = "Bearer token";

    @BeforeEach
    void setUp() {
        controller = new NotificationController();
        ReflectionTestUtils.setField(controller, "notificationService", notificationService);
    }

    @Test
    void markNotificationAsReadDelegatesToService() {
        controller.markNotificationAsRead(AUTH, 1L);
        verify(notificationService).markAsRead(AUTH, 1L);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw.cmd -q test -Dtest=NotificationControllerTest`
Expected: COMPILE ERROR — `NotificationController` does not exist yet.

- [ ] **Step 3: Create the controller**

Create `src/main/java/com/example/demo/Controller/NotificationController.java`:

```java
package com.example.demo.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.Service.NotificationService;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.NotificationToggleDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "通知 API", description = "提供登入使用者操作自己的通知")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @Operation(summary = "標記通知為已讀", description = "將指定通知標記為已讀，僅限通知擁有者操作")
    @PostMapping("/api/notification/{id}/isRead")
    public ApiResponse<NotificationToggleDto> markNotificationAsRead(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long id) {
        return notificationService.markAsRead(authorizationHeader, id);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw.cmd -q test -Dtest=NotificationControllerTest`
Expected: PASS (no output in quiet mode, exit code 0)

- [ ] **Step 5: Run the full test suite**

Run: `./mvnw.cmd -q test`
Expected: BUILD SUCCESS (no output in quiet mode, exit code 0)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/demo/Controller/NotificationController.java src/test/java/com/example/demo/Controller/NotificationControllerTest.java
git commit -m "feat: 新增 POST /api/notification/{id}/isRead API"
```
