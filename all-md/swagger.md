# Swagger / OpenAPI 文件

更新日期：2026-07-07

本文件說明目前 `demo` 專案的 Swagger / OpenAPI 設定、DTO 標註方式、JWT 使用方式，以及目前 API 清單。

## Swagger UI

啟動 Spring Boot 後可開啟：

```text
http://localhost:8081/swagger-ui/marketDay/index.html
```

OpenAPI JSON：

```text
http://localhost:8081/v3/api-docs
```

Swagger UI 路徑由 `application.properties` 設定：

```properties
springdoc.swagger-ui.path=/swagger-ui/marketDay/index.html
```

## OpenAPI 設定

OpenAPI 設定檔：

```text
src/main/java/com/example/demo/swagger/OpenApiConfig.java
```

目前設定內容包含 API 標題、版本資訊與 JWT Bearer security scheme。
Swagger UI 使用的授權名稱為：

```text
bearerAuth
```

需要在 Swagger 顯示 JWT 授權的 Controller method，可以使用：

```java
@SecurityRequirement(name = "bearerAuth")
```

## DTO 架構

| 類型 | 位置 | 用途 |
| --- | --- | --- |
| Request DTO | `src/main/java/com/example/demo/dto/request` | 接收 request body。 |
| Response DTO | `src/main/java/com/example/demo/dto/response` | 放入 `ApiResponse<T>.data`。 |
| API wrapper | `src/main/java/com/example/demo/dto/response/ApiResponse.java` | 統一回傳 `statusCode`、`message`、`messageDetails`、`data`。 |

Controller 應直接回傳 `ApiResponse<T>`，不再以 `Map<String, Object>` 作為正式 response。

```java
@PostMapping("/api/vendor/local-login")
public ApiResponse<LoginResponse> vendorLogin(@Valid @RequestBody LocalLoginRequest body) {
    return userService.loginLocal(body, "VENDOR");
}
```

## Request DTO 標註

Request DTO 使用 `@Schema` 與 Bean Validation 標註欄位。

```java
package com.example.demo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "本地登入請求")
public class LocalLoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Schema(description = "登入 Email", example = "user@example.com")
    private String email;
}
```

常見驗證規則：

| 欄位 | 規則 |
| --- | --- |
| `email` | 必填，需符合 Email 格式。 |
| `password` | 必填，至少 8 碼，需包含英文與數字。 |
| `name` | 本地註冊必填，最多 20 個字元。 |
| `code` | 必填，6 位數驗證碼。 |
| `resetToken` | 重設密碼時必填。 |
| `applicationNo` | 攤位選擇與攤位圖查詢使用。 |
| `stallNo` | 攤位選擇使用，例如 `A01`。 |
| `credential` | Google 登入 / 註冊 credential。 |

DTO 驗證錯誤會由 `GlobalExceptionHandler` 回傳中文訊息。

## 統一 API Response

目前所有 Controller 回傳 `ApiResponse<T>`。

```json
{
  "statusCode": 200,
  "message": "Login successful",
  "messageDetails": null,
  "data": {}
}
```

| 欄位 | 說明 |
| --- | --- |
| `statusCode` | 成功通常為 `200`，一般錯誤為 `400`，JWT 錯誤為 `401`。 |
| `message` | 成功或錯誤訊息；錯誤會統一轉為中文。 |
| `messageDetails` | 目前直接回傳 `ApiResponse<T>`，通常為 `null`。 |
| `data` | 成功時放 Response DTO；錯誤時通常為 `null`。 |

詳細 response 範例請看：

```text
api-response.md
```

## 中文錯誤訊息

錯誤請使用：

```java
return ApiResponse.fail("Email already registered");
```

實際回傳會由 `ApiResponse.fail(...)` 轉成中文：

```json
{
  "statusCode": 400,
  "message": "此 Email 已被註冊",
  "messageDetails": null,
  "data": null
}
```

常見錯誤：

| 原始 key | 實際回傳中文 |
| --- | --- |
| `Email already registered` | `此 Email 已被註冊` |
| `Invalid email or password` | `Email 或密碼錯誤` |
| `Authorization token is required` | `請提供授權 token` |
| `Invalid or expired token` | `Token 無效或已過期` |
| `Session expired` | `登入狀態已過期，請重新登入` |

## JWT 使用方式

Swagger UI 測試需要 JWT 的 API 時：

1. 先呼叫登入 API 取得 JWT。
2. 點選 Swagger UI 的 `Authorize`。
3. 輸入：

```text
Bearer <JWT_TOKEN>
```

## JWT Protected APIs

目前 `JwtAuthenticationFilter.protectedApis` 是需要 JWT 驗證 API 的單一清單來源。

| Method | API |
| --- | --- |
| POST | `/api/auth/logout` |
| POST | `/api/auth/google-bind` |
| GET | `/api/auth/me` |
| POST | `/api/account/deactivate` |
| GET | `/api/vendor/account` |
| POST | `/api/vendor/applications` |
| GET | `/api/vendor/stall-map/{applicationNo}` |
| POST | `/api/stalls/select` |
| GET | `/api/organizer/account` |
| GET | `/api/organizer/accounts/{eventId}` |
| GET | `/api/organizer/accounts/{eventId}/export` |
| GET | `/api/organizer/stalls/search` |
| GET | `/api/organizer/equipment/search` |
| GET | `/api/organizer/equipment/{eventId}` |
| GET | `/api/organizer/equipment/{eventId}/export` |
| GET | `/api/organizer/stall/{eventId}` |
| GET | `/api/organizer/stall/{eventId}/{stallNo}` |
| GET | `/api/organizer/applications/search` |
| GET | `/api/organizer/applications/{id}` |
| POST | `/api/organizer/applications/{id}/approve` |
| POST | `/api/organizer/applications/{id}/reject` |

公開 API 例如註冊、登入、Email 驗證、忘記密碼、重設密碼與攤位狀態查詢，不放在 `protectedApis`。

## API 清單

### 使用者與驗證 API

| Method | API | Request DTO | JWT | 說明 |
| --- | --- | --- | --- | --- |
| GET | `/usersall` | - | 否 | 查詢所有使用者。 |
| POST | `/api/vendor/local-register` | `LocalRegisterRequest` | 否 | 攤主本地註冊。 |
| POST | `/api/organizer/local-register` | `LocalRegisterRequest` | 否 | 主辦方本地註冊。 |
| POST | `/api/vendor/google-register` | `GoogleCredentialRequest` | 否 | 攤主 Google 註冊。 |
| POST | `/api/organizer/google-register` | `GoogleCredentialRequest` | 否 | 主辦方 Google 註冊。 |
| POST | `/api/vendor/local-login` | `LocalLoginRequest` | 否 | 攤主本地登入。 |
| POST | `/api/organizer/local-login` | `LocalLoginRequest` | 否 | 主辦方本地登入。 |
| POST | `/api/admin/local-login` | `LocalLoginRequest` | 否 | 管理員本地登入。 |
| POST | `/api/vendor/google-login` | `GoogleCredentialRequest` | 否 | 攤主 Google 登入。 |
| POST | `/api/organizer/google-login` | `GoogleCredentialRequest` | 否 | 主辦方 Google 登入。 |
| POST | `/api/auth/google-bind` | `GoogleCredentialRequest` | 是 | 綁定目前登入帳號與 Google。 |
| POST | `/api/auth/createAccount/emailVerify` | `EmailVerificationRequest` | 否 | 註冊 Email 驗證。 |
| POST | `/api/auth/resetPassword/request` | `RequestPasswordResetRequest` | 否 | 申請重設密碼驗證碼。 |
| POST | `/api/auth/resetPassword/emailVerify` | `EmailVerificationRequest` | 否 | 驗證重設密碼 Email 驗證碼，成功後回傳 reset token。 |
| POST | `/api/auth/resetPassword/reset` | `ResetPasswordRequest` | 否 | 使用 reset token 重設密碼。 |
| POST | `/api/auth/logout` | - | 是 | 登出。 |
| GET | `/api/auth/me` | - | 是 | 取得目前登入使用者資料。 |
| POST | `/api/account/deactivate` | - | 是 | 停用目前登入帳號。 |

### 攤主與攤位 API

| Method | API | Request DTO | JWT | 說明 |
| --- | --- | --- | --- | --- |
| POST | `/api/stalls/select` | `StallSelectionRequest` | 是 | 依 `applicationNo` 與 `selections[]` 一次送出該申請單所有報名日期的選位。 |
| GET | `/api/eventsMap/{eventId}/stallsStatus` | - | 否 | 公開查詢活動指定日期攤位狀態；未帶日期時預設活動第一天。 |
| GET | `/api/vendor/account` | - | 是 | 取得目前登入攤主資料。 |
| POST | `/api/vendor/applications` | `VendorApplicationSubmitRequest` | 是 | 攤主送出活動報名資料，建立申請單、報名日期、租借設備與用電電器明細。 |
| POST | `/api/vendor/markets/search` | Query params | 否 | 取得活動報名列表，支援活動名稱、報名編號、狀態、活動日期區間與分頁。 |
| GET | `/api/vendor/markets/{id}` | - | 否 | 依報名 ID 取得活動報名詳細資料。 |
| GET | `/api/vendor/stall-map/{applicationNo}` | - | 是 | 查詢待選位或已成功選位申請單的攤位圖；已選位時回傳 `selectedStall`。 |

#### 攤主活動報名送出 API

`POST /api/vendor/applications`

```json
{
  "eventId": 1,
  "applyDates": ["2026-08-01", "2026-08-02"],
  "vehicleNo": "ABC-1234",
  "applicantNote": "需要靠近出入口的位置",
  "equipmentRentals": [
    {
      "eventEquipmentId": 3,
      "quantity": 1,
      "rentalUnits": 2,
      "appliances": [
        {
          "applianceName": "咖啡機",
          "wattage": 800
        }
      ]
    }
  ]
}
```

- 需要 JWT，且登入者必須是 `VENDOR`。
- 活動必須是 `PUBLISHED`，且目前時間必須落在活動報名期間內。
- `applyDates[]` 會去重排序，每個日期都必須落在活動開始與結束日期內。
- 同一個攤主品牌在同一活動只能建立一筆報名。
- 設備租借會檢查設備是否屬於該活動、是否啟用，以及是否超過庫存或單攤租借上限。
- 成功後回傳 `VendorApplicationSubmitResponse`，狀態預設為 `待審核`。

#### 攤位選位 API

`POST /api/stalls/select`

```json
{
  "applicationNo": "MD001",
  "stallNo": "A01"
}
```

- `eventId` 不由前端傳入，後端會由 `applicationNo` 查出申請單所屬活動。
- 需要 JWT，且登入者必須是 `VENDOR`。
- `applicationNo` 與 `stallNo` 只檢查必填，不限制編號格式。
- 申請單必須屬於目前登入攤主。
- 申請單必須已審核通過、已付款、尚未選位。
- 選位時會先把攤位從 `AVAILABLE` 更新為 `SELECTED`，搶位失敗會回 `此攤位已被選走`。
- 錯誤訊息已拆分為申請單狀態問題與攤位選位機制問題：
  - 申請單狀態：找不到申請資料、非本人申請、已取消、尚在審核中、審核未通過、尚未通過審核、尚未付款、付款狀態不可選位、已完成選位。
  - 攤位機制：找不到攤位資料、攤位不可選擇、攤位已被選走。

#### 攤位地圖 API

`GET /api/vendor/stall-map/{applicationNo}`

- 需要 JWT，且登入者必須是 `VENDOR`。
- 申請單必須屬於目前登入攤主。
- 可查看地圖的狀態：
  - `待選位`：用於進入選位。
  - 已付款且已有 `selectedStallId`：用於查看已成功選位的攤位資料。
- 其他狀態會回 `此申請單目前不可查看攤位地圖`。
- `application` 會回傳 `applicationStatus`；已選位時會回傳 `selectedStall`。

### 主辦方 API

| Method | API | Request | JWT | 說明 |
| --- | --- | --- | --- | --- |
| GET | `/api/organizer/account` | Authorization header | 是 | 取得目前登入主辦方資料。 |
| GET | `/api/organizer/accounts/search` | Query params | 是 | 查詢主辦方帳務活動列表，可依活動名稱、狀態、活動日期與 `page`/`pageSize` 分頁篩選。 |
| GET | `/api/organizer/accounts/{eventId}` | Query params | 是 | 查詢活動帳務詳情，可依帳務狀態篩選付款明細，並以 `paymentPage`/`paymentPageSize` 分頁。 |
| GET | `/api/organizer/equipment/search` | Query params | 是 | 查詢主辦方設備租借活動列表，可依活動名稱、狀態、活動日期與 `page`/`pageSize` 分頁篩選。 |
| GET | `/api/organizer/equipment/{eventId}` | Query params | 是 | 查詢主辦方活動設備、用電、租借統計與管理列表；管理列表可各自分頁。 |
| GET | `/api/organizer/applications/search` | Query params | 是 | 查詢目前主辦方 published 活動的申請資料，支援條件與 `page`/`pageSize` 分頁篩選。 |
| GET | `/api/organizer/applications/{id}` | Authorization header | 是 | 查詢主辦方申請明細。 |
| GET | `/api/organizer/stalls/search` | Query params | 是 | 查詢主辦方攤位管理活動列表，可用 `page`/`pageSize` 分頁。 |
| GET | `/api/organizer/stall/{eventId}` | Query params | 是 | 查詢主辦方活動指定日期攤位狀況，可依關鍵字與選位狀態篩選。 |
| GET | `/api/organizer/stall/{eventId}/{stallNo}` | Query params | 是 | 查詢主辦方活動指定日期單一攤位的攤主與申請資訊。 |

## Organizer 帳務詳情

`GET /api/organizer/accounts/{eventId}`

- 需要 `Authorization` header。
- `eventId` 放在 path params。
- `status` query param 可篩選付款明細，支援 `付款成功`、`退款處理中`、`退款申請中`、`已退款`、`已取消`。
- `paymentPage`、`paymentPageSize` 可控制 `payments` 分頁；`pageSize` 最大 10 筆。
- 回傳 `event`、`summary`、`statistics`、`payments` 四個主要區塊。
- `payments` 回傳 `totalCount`、`items`、`page`、`pageSize`、`totalItems`、`totalPages`、`hasPrevious`、`hasNext`。
- `payments` 不回傳 `stallNo`。
- `payments.refundAmount` 只代表已完成退款金額；退款申請中與退款處理中的明細會回 `0`，且不納入帳務摘要退款總額。

## Organizer 設備詳情

`GET /api/organizer/equipment/{eventId}`

- 需要 `Authorization` header。
- `eventId` 放在 path params。
- 回傳活動資訊、設備提供狀況、基本用電、加購用電、設備租借統計、用電統計、車牌統計與管理列表。
- `equipmentRentalManagement` 可用 `equipmentRentalPage`、`equipmentRentalPageSize` 分頁。
- `extraPowerManagement` 可用 `extraPowerPage`、`extraPowerPageSize` 分頁。
- `vehicleManagement` 可用 `vehiclePage`、`vehiclePageSize` 分頁。
- 三個管理列表皆回傳 `totalCount`、`items`、`page`、`pageSize`、`totalItems`、`totalPages`、`hasPrevious`、`hasNext`；`pageSize` 最大 10 筆。
- Excel 匯出 API 不套用上述 detail 分頁參數，仍輸出完整資料。

## Organizer 申請列表

`GET /api/organizer/applications/search`

- 需要 `Authorization` header。
- 不接收 request body。
- 回傳目前登入主辦方的全部申請資料。
- 依 `event_applications.created_at DESC, event_applications.id DESC` 排序。
- `data` 型別為 `List<OrganizerApplicationSummaryResponse>`。

主要欄位：

| 欄位 | 說明 |
| --- | --- |
| `applicationId` | 申請 ID。 |
| `applicationNo` | 申請編號。 |
| `eventId` | 活動 ID。 |
| `eventTitle` | 活動名稱。 |
| `eventTime` | 活動時間文字。 |
| `eventStartAt` | 活動開始日期時間。 |
| `eventEndAt` | 活動結束日期時間。 |
| `applyDates` | 申請日期，逗號分隔。 |
| `vendorName` | 品牌 / 攤主名稱。 |
| `vendorOwnerName` | 攤主姓名。 |
| `brandType` | 品牌類型。 |
| `appliedAt` | 申請時間。 |
| `applicationStatus` | 後端計算後的顯示狀態。 |

## Organizer 申請明細

`GET /api/organizer/applications/{id}`

`data` 型別為 `OrganizerApplicationDetailResponse`。

| 區塊 | 說明 |
| --- | --- |
| `application` | 申請 ID、申請編號與後端計算後的申請狀態。 |
| `event` | 活動名稱、活動狀態、活動日期與地址；`eventStatus` 固定為 `活動預告`、`即將開始`、`進行中`、`已結束`。 |
| `vendor` | 攤主聯絡資訊。 |
| `brand` | 品牌資訊。 |
| `applicationdetail` | 報名時段、攤位尺寸、攤位區域、攤位類別、車牌、申請備註與審核備註；`registrationPeriods` 為單一字串。 |
| `stall` | 依報名日期回傳攤位列；每列包含日期、攤位編號、區域與選擇狀態。 |
| `fee` | 付款狀態、付款方式、付款編號與付款金額。 |
| `feedetail` | 報名費、設備租借費、額外電費、保證金與總計。 |
| `equipmentRentals` | 四區設備/用電資訊：`freeEquipments`、`freeBasicPower`、`rentalEquipments`、`extraPower`。 |
| `status` | 固定狀態流清單與各節點時間。 |

`applicationdetail.registrationPeriods` 範例：

```text
2026-06-28 11:00-19:00 - 2026-06-29 11:00-19:00
```

`feedetail.content` 範例：

| 項目 | content 範例 |
| --- | --- |
| 報名費 | `2天 (2026-06-28、2026-06-29)` |
| 設備租借費 | `帳篷租借*1、冷藏櫃租借*1` |
| 額外電費 | `110V / 500W*2` |

`rentalEquipments.unit` 與 `extraPower.unit` 只回單位文字，例如 `天`，不包含 `/`。

## 文件維護規則

- 新增或調整 API 時，同步更新 `README.md`、`api-response.md`、`swagger.md`。
- `README.md` 的更新紀錄請放在「更新日誌」下方，並以日期分區。
- 新增需要 JWT 驗證的 API 時，請同步更新 `JwtAuthenticationFilter.protectedApis` 與本文件的 JWT Protected APIs 表格。
- 新增 request body 時，請建立或更新 `dto/request`。
- 新增 response data 時，請建立或更新 `dto/response`。
- 錯誤訊息請透過 `ApiResponse.fail(...)` 回傳，讓前端收到中文錯誤訊息。

## 2026-07-01 更新：Organizer 報名審核 API

JWT protected APIs 已新增：

| Method | API |
| --- | --- |
| POST | `/api/organizer/applications/{id}/approve` |
| POST | `/api/organizer/applications/{id}/reject` |

### 審核通過

`POST /api/organizer/applications/{id}/approve`

- 需要 `Authorization` header。
- `id` 放在 path params。
- 不需要 request body。
- 成功後更新 `event_applications.review_status = APPROVED`。

### 審核不通過

`POST /api/organizer/applications/{id}/reject`

- 需要 `Authorization` header。
- `id` 放在 path params。
- Request DTO: `OrganizerApplicationReviewRequest`。
- `reviewNote`、`reviewNoteDetail` 皆非必填。
- 成功後更新 `event_applications.review_status = REJECTED`。
- 退件原因會以 JSON 字串存入 `event_applications.review_note`，不新增 SQL 欄位。

Request body:

```json
{
  "reviewNote": "資料不完整",
  "reviewNoteDetail": "請補上商品照片"
}
```
