# Notification 標記已讀 API 設計

## 背景

目前 `Notification` 實體已有 `isRead` / `readAt` 欄位，但沒有任何 API 可以將通知標記為已讀。也還沒有通用的 `NotificationController`，通知的讀取目前分散在 `OrganizerController`（`/api/organizer/notices`）與 `VendorController`（`/api/vendor/notices`），各自委派給 `OrganizerNotificationService` / `VendorNotificationService`。

## API 規格

- **Endpoint**: `POST /api/notification/{id}/isRead`
- **Path 參數**: `id`（Long，通知 ID）
- **Header**: `Authorization: Bearer <token>`（必填，任一已登入角色皆可使用，不限 organizer/vendor）
- **回傳**: `ApiResponse<NotificationToggleDto>`

```java
public record NotificationToggleDto(Long id, boolean isRead) {}
```

## 權限與擁有者驗證

1. 從 `Authorization` header 解析 JWT：`JwtService.extractTokenFromAuthorizationHeader` → `isTokenValid` → `getEmail`。
2. 用 email 查出目前登入的 `User`。`UserRepo`（JPA）目前沒有 `findByEmail`，需新增這個 derived query method：`Optional<User> findByEmail(String email)`。
3. 用 `NotificationRepo.findById(id)`（JPA）撈出通知：
   - 不存在 → `ApiResponse.fail("Notification not found")`
4. 比對 `notification.getUser().getId()` 與目前登入者 id：
   - 不符 → `ApiResponse.fail("Notification does not belong to this account")`
   - token 缺失/無效 → 沿用既有慣例訊息（如 `"Authorization token is required"` / `"Invalid or expired token"`）

## 更新邏輯（含冪等性）

- 若 `isRead` 已經是 `true`：不做任何寫入，直接回傳目前狀態，視為成功（冪等）。
- 否則：設定 `isRead = true`、`readAt = LocalDateTime.now()`，呼叫 `NotificationRepo.save(notification)`。
- 成功回傳：`ApiResponse.success("Notification marked as read", new NotificationToggleDto(notification.getId(), notification.getIsRead()))`。

## 程式碼位置

- 新建 `Controller/NotificationController.java`（`@RequestMapping("/api/notification")`）。
- 在既有 `Service/NotificationService.java` 新增：
  - `markAsRead(String authorizationHeader, Long id)` 方法（含以上驗證與更新邏輯）
  - 私有 `authenticatedUser(String authorizationHeader)` helper，回傳格式仿照 `OrganizerNotificationService.authenticatedOrganizer` 的 `Map<String, Object>`（含 `message` 或 `userId`）寫法
  - 新增依賴注入：`JwtService`、`UserRepo`、`NotificationRepo`（JPA 版）
- 新增 DTO：`dto/response/NotificationToggleDto.java`
- `ApiResponse` 的 `toChineseErrorMessage` / `toChineseSuccessMessage` 需要補上新訊息字串（`"Notification not found"`、`"Notification does not belong to this account"`、`"Notification marked as read"`）的中文對應，否則會落到 fallback 訊息。

## 資料存取層

沿用既有 JPA `NotificationRepo`（`findById` + entity mutation + `save()`），不動用 JdbcTemplate 版的 `NotificationRepository`，維持程式碼最簡單。

## 不在此次範圍內

- 查詢單筆通知的 API（`GET /api/notification/{id}`）
- 一次標記全部通知為已讀的 API
- 這兩項使用者已確認暫不需要，之後有需求再另外設計。

## 測試

`NotificationService` 單元測試涵蓋：

- 成功標記已讀
- 對已讀通知重複呼叫（冪等，不應更新 `readAt`）
- id 不存在
- 通知不屬於此帳號（ownership 不符）
- token 缺失 / 無效
