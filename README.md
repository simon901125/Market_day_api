# Market Day API

Market Day 後端 API 專案，使用 Spring Boot 建置，包含帳號註冊、登入、Google 登入、JWT 驗證、攤位選擇、活動攤位狀態、主辦方資料與主辦方申請查詢。

## 更新日誌

> 更新日誌請依日期與 branch 分區：日期使用 `###`，branch 使用 `####`，避免不同分支的更動混在同一段。

### 2026-07-09

#### simon branch

- `POST /api/auth/resetPassword/reset` 調整密碼重設優先順序：若有帶入有效 `Authorization: Bearer JWT`，會直接以目前登入者更新密碼；未帶登入 token 時才改用 `resetToken` 驗證。
- `GET /api/organizer/equipment/search` 新增主辦方設備租借活動列表，支援依活動名稱、狀態與活動日期區間篩選，回傳設備租借、用電租借與車牌登記統計。
- `GET /api/organizer/equipment/{eventId}` 新增主辦方活動設備詳情，回傳活動資訊、設備提供狀況、基本用電、額外用電、設備租借統計、額外用電統計、車牌統計與管理列表。
- `GET /api/organizer/equipment/{eventId}` 的 `eventEquipments` 會依 `item_type + equipment_group_key` 合併同一設備品項的免費與付費設定；`dailyRentableQuantity` 為免費庫存加付費庫存總數。
- `GET /api/organizer/equipment/{eventId}` 的 `equipmentRentalStatistics` 會依同一設備品項彙整免費與付費租借統計，不再將同品項拆成兩列。
- `GET /api/organizer/equipment/{eventId}` 的 `extraPowers` 改以 `perStallProvidedQuantity` 表示每攤可提供組數，`availableGroupQuantity` 表示此用電方案可提供組數。
- 補齊 `OrganizerController` 內設備租借與報名詳情 API 的中文 Swagger 註解。

### 2026-07-07

#### simon branch

- `GET /api/organizer/applications/{id}` 的 `event` 新增 `eventStatus`，統一回傳 `活動預告`、`即將開始`、`進行中`、`已結束`。
- `GET /api/organizer/applications/{id}` 新增 `applicationdetail`，包含單一字串格式的 `registrationPeriods`、攤位尺寸、攤位區域、攤位類別、車牌、申請備註與審核備註。
- `GET /api/organizer/applications/{id}` 的 `stall` 改為依報名日期回傳陣列；每列包含日期、攤位編號、攤位區域與選擇狀態，未選位日期也會保留一列。
- `GET /api/organizer/applications/{id}` 的 `fee` 簡化為付款狀態、付款方式、付款編號與付款金額；新增 `feedetail` 回傳報名費、設備租借費、額外電費、保證金與總計。
- `GET /api/organizer/applications/{id}` 的 `equipmentRentals` 改為四區：`freeEquipments`、`freeBasicPower`、`rentalEquipments`、`extraPower`；付費租借與額外用電的 `unit` 只回單位文字。
- 新增 `GET /api/organizer/stalls/search` 主辦方攤位管理活動列表，支援依活動名稱、狀態、開始日期與結束日期篩選；回傳活動名稱、活動日期、活動地點、攤位總數與前端列表狀態。
- 主辦方攤位詳情 API 由 `GET /api/organizer/stall-map/{eventId}` 改為 `GET /api/organizer/stall/{eventId}`，輸入維持 `eventId` 與 `applyDate`，並新增 `keyword`、`status` 篩選攤位列表。
- `GET /api/organizer/stall/{eventId}` 的活動資訊新增 `locationName`、`eventStatus`、`totalStallCount`；已選攤位會回傳 `selectedVendor`，包含品牌名稱、品牌類型、攤主姓名與選位時間。
- 主辦方單一攤位詳情 API 由 `GET /api/organizer/stall-map/{eventId}/stalls/{stallNo}` 改為 `GET /api/organizer/stall/{eventId}/{stallNo}`，用於點擊攤位後查詢該攤位與已選攤主/申請資料。
- 攤位管理的 `已額滿` 判定改為活動期間每一天都已選滿攤位才成立；只要任一天仍有可選攤位，就不會顯示 `已額滿`。
- 新增 `GET /api/organizer/accounts/search` 主辦方帳務活動列表，支援活動名稱、狀態與活動日期區間篩選，並回傳收款總額、退款總額、已退/未退保證金與實收總額。
- 新增 `GET /api/organizer/accounts/{eventId}` 主辦方活動帳務詳情，回傳活動資訊、帳務摘要、付款/退款/保證金統計與付款明細。
- `GET /api/organizer/accounts/{eventId}` 的 `payments` 明細不回傳 `stallNo`；`refundAmount` 只在退款狀態為 `REFUNDED` 時納入計算與顯示，退款申請中與退款處理中固定回 `0`。



### 2026-07-06

#### yushuan branch

- `POST /api/markets/search` 新增市集活動列表查詢 API，支援依關鍵字、縣市、活動狀態、活動日期區間、分類名稱與活動類型篩選市集活動；回傳已發布且審核通過的市集活動列表，並包含活動狀態與分類資料。
- `GET /api/markets/{id}` 新增市集活動詳細資料 API，依市集活動 ID 查詢單筆已發布且審核通過的活動詳情；回傳活動介紹、地點、日期時間、報名時間、攤位資訊、費用、封面圖、地圖圖、分類與活動狀態等資料。

### 2026-07-02

#### simon branch

- `POST /api/stalls/select` 改為依 `applicationNo` 搭配多筆 `selections` 選位，每筆包含 `applyDate` 與 `stallNo`，且送出的日期必須完整對應該申請單的報名日期。
- `GET /api/vendor/stall-map/{applicationNo}` 支援 `applyDate` 切換目前查看日期；未帶日期時預設該申請單第一個報名日，並回傳 `applyDates`、`applyDateCount`、`currentApplyDate` 與 `selectedStall`。
- 新增公開 API `GET /api/eventsMap/{eventId}/stallsStatus` 至 `AllController`，不需 JWT；未帶 `applyDate` 時預設活動第一天，並回傳攤位尺寸、狀態、攤主名稱與目前日期等地圖欄位。
- 移除攤主端舊的 `GET /api/events/{eventId}/stallsStatus` 使用方式，活動公開攤位狀態統一改由 `/api/eventsMap/{eventId}/stallsStatus` 查詢。
- 主辦方地圖 API 支援依 `applyDate` 查看每日選位狀況；未帶日期時預設活動第一天。
- 補齊攤位相關 Swagger 中文註解與 `ApiResponse` 中文錯誤訊息 mapping。

### 2026-07-01

#### simon branch

- `GET /api/organizer/applications/{id}` 回傳補上租借設備資料 `equipmentRentals`，並讓費用區塊使用實際租借明細加總。
- `GET /api/organizer/applications/{id}` 的 `status` 改為固定狀態流清單，包含報名、審核、取消、付款、退款申請、退款審核、已退款、選位、保證金退還；未到達的節點回傳 `value: null` 與 `createdAt: null`。
- `GET /api/organizer/applications/{id}` 當時曾在 `fee` 補上費用 note；目前格式已由 2026-07-07 的簡化 `fee` 與 `feedetail` 取代。
- `ApiResponse.success(...)` 與 `ApiResponse.fail(...)` 訊息統一轉為中文；未列入 mapping 的英文成功訊息會 fallback 為 `操作成功`。
- `POST /api/stalls/select` 的 `StallSelectionRequest` 移除 `applicationNo`、`stallNo` 的格式 pattern 限制，僅保留必填檢查。
- `POST /api/stalls/select` 的錯誤訊息拆分為申請單狀態問題與攤位選位機制問題，方便測試時判斷是審核、付款、已選位、攤位不存在或攤位已被選走。
- 新增 API 請求紀錄流程：`RequestLoggingFilter` 只記錄會異動資料的 API，並透過 `request_logs.status_code` 保存實際回傳結果。
- 新增 `status_logs` 寫入流程，將狀態異動集中在 `StatusLogService` 規則表中處理，支援登入、登出、停用帳號、Email 驗證與選位狀態紀錄。
- `GET /api/organizer/applications/{id}` 的 `status` 回傳改為包含 `value` 與 `createdAt`，時間優先來自 `status_logs` 關聯的 `request_logs.created_at`。
- 自動登出排程會以 `SYSTEM / AUTO_LOGOUT_EXPIRED_USERS` 寫入 `request_logs`，並為被登出的使用者寫入 `users.isLogin = false` 的 `status_logs`。
- 新增 `StatusLogServiceTest`，覆蓋目前所有 status log API 規則與自動產生的狀態紀錄內容。

### 2026-06-29

#### simon branch

- 選位 API 改為 `POST /api/stalls/select`，request body 只需 `applicationNo` 與 `stallNo`；後端會由 `applicationNo` 查出 `eventId`。
- 選位流程改為先搶攤位狀態，再綁定申請單，避免多人同時選到同一攤位時出現競爭問題。
- 選位 API 補上登入者必須為攤主，且申請單必須屬於目前登入攤主的檢查。
- `GET /api/vendor/stall-map/{applicationNo}` 僅允許 `待選位` 或已成功選位的有效申請單查看地圖；已成功選位時回傳 `selectedStall` 供攤主確認。
- `GET /api/vendor/stall-map/{applicationNo}` 的 `application` 回傳新增 `applicationStatus`。
- 回傳 JSON 中 `address` 欄位統一組成 `city + district + address`。
- `sql/test3.sql` 測資改為符合 request DTO：申請單號 `MD001` 到 `MD100`、攤位編號 `A01` 到 `A40`，資料量維持 20 攤主、2 主辦、10 活動、100 申請單。
- `GET /api/organizer/applications/search` 目前會回傳登入中主辦方的全部申請資料，不接收 request body。
- 保留 `GET /api/organizer/applications/{id}` 作為主辦方申請明細 API。
- `LocalRegisterRequest` 只接受 `name`、`email`、`password`；註冊時會建立最小 `user_profiles`，只預填 `name`。
- `users` 已移除 `name`、`phone`；使用者顯示名稱改由 `user_profiles.name` 回傳。
- `user_profiles.contact_name`、`contact_phone`、`contact_email` 不會在註冊時預先填入，聯絡資料留待登入後補基本資料。
- Google 註冊只接受 `GoogleCredentialRequest.credential`，並以 Google 回傳的 `name` 建立 `user_profiles.name`。
- `POST /api/auth/google-bind` 用於 Google 綁定，需登入 LOCAL 帳號，Google email 必須與目前帳號 email 相同，且 `google_sub` 尚未被使用。
- `JwtAuthenticationFilter.protectedApis` 為目前需要 JWT 驗證的 API 清單來源。
- `isCurrentLoginSession` 不再額外檢查 `status = 'ACTIVE'`；登入流程本身已限制未啟用帳號。
- 錯誤訊息已透過 `ApiResponse.fail(...)` 統一轉為中文，例如 `Email already registered` 會回傳 `此 Email 已被註冊`。
- `api-response.md` 已更新為目前 `ApiResponse<T>` 與 DTO 架構版本。
- `swagger.md` 已同步目前 Swagger、DTO、JWT protected APIs 與中文錯誤訊息說明。
- `market_events` 活動時間欄位改為 `start_at`、`end_at`、`registration_start_at`、`registration_end_at` 四個 `DATETIME2(0)` 欄位；主辦方申請與攤主選位地圖 API 已同步改用 `eventStartAt/eventEndAt`、`startAt/endAt`。
- `users.status` 停用狀態改用 `DISABLED`；`POST /api/account/deactivate` 會將帳號狀態更新為 `DISABLED`。
- `market_events.workflow_status` 新增 `UNPUBLISH_REQUESTED`，並新增 `event_unpublish_requests` 支援下架申請流程。
- 新增 `notifications.is_read/read_at` 支援通知中心未讀/已讀狀態。
- 新增 `admin_operation_logs` 作為管理員後台操作紀錄表。

### 2026-06-26

- 移除攤主選位地圖回傳中的底圖資訊，不再回傳 `mapImageUrl`。
- 移除專案對 `uploads` 靜態資源路徑的參考，包含 `app.upload-dir`、`app.upload-url-prefix` 與 `UploadResourceConfig`。
- 將 DB、Google OAuth、Mail 等本機私密設定改為透過環境變數提供。
- 改用 `run-local.cmd` 作為本機啟動入口，先設定環境變數，再啟動 Spring Boot。
- 將 `run-local.cmd` 加入 `.gitignore`，避免本機私密資料被提交。
- 將 `/api/organizer/applications/search` 調整為 GET 查詢，不接收 request body。
- 將 `GET /api/organizer/applications/search` 加入 `JwtAuthenticationFilter.protectedApis`。
- 新增 `GET /api/organizer/applications/{id}` 主辦方申請明細 API。

### 2026-06-25

- 新增主畫面相關 `MainScreenController`、`MainScreenService`、`MainScreenRepository` 與 `/api/main-screen` API。
- 新增主辦方查詢申請列表與申請狀態顯示邏輯。
- 調整 DB 狀態欄位與 `ApplicationStatusService` 顯示狀態。

## 技術

- Java 21
- Spring Boot 3.5.14
- Spring Web
- Spring Data JDBC
- SQL Server
- Bean Validation
- JWT：jjwt 0.12.6
- Swagger / OpenAPI：springdoc-openapi 2.8.17
- Maven Wrapper

## 專案結構

```text
demo/
├─ pom.xml
├─ mvnw / mvnw.cmd
├─ run-local.cmd
├─ README.md
├─ api-response.md
├─ swagger.md
├─ src/main/java/com/example/demo
│  ├─ Config/
│  ├─ Controller/
│  ├─ Filter/
│  ├─ Repository/
│  ├─ Service/
│  ├─ dto/
│  │  ├─ request/
│  │  └─ response/
│  └─ swagger/
└─ src/main/resources/
   ├─ application.properties
   └─ static/
```

## 設定

主要環境變數：

| 變數                  | 說明                        |
| --------------------- | --------------------------- |
| `DB_URL`            | SQL Server JDBC URL。       |
| `DB_USERNAME`       | SQL Server 帳號。           |
| `DB_PASSWORD`       | SQL Server 密碼。           |
| `GOOGLE_CLIENT_ID`  | Google OAuth Client ID。    |
| `MAIL_USERNAME`     | Gmail 帳號。                |
| `MAIL_PASSWORD`     | Gmail App Password。        |
| `JWT_SECRET`        | JWT secret。                |
| `JWT_EXPIRATION_MS` | JWT 有效時間，預設 1 小時。 |
| `ADMIN_EMAIL`       | 系統初始化管理員 Email。    |
| `ADMIN_PASSWORD`    | 系統初始化管理員密碼。      |

本機可透過 `run-local.cmd` 設定環境變數並啟動 Spring Boot。

## 執行

```powershell
cd demo
.\mvnw.cmd spring-boot:run
```

測試：

```powershell
cd demo
.\mvnw.cmd test
```

## Swagger

Swagger UI：

```text
http://localhost:8081/swagger-ui/marketDay/index.html
```

OpenAPI JSON：

```text
http://localhost:8081/v3/api-docs
```

詳細 Swagger / OpenAPI 文件請看：

```text
swagger.md
```

## 統一 API Response

所有 Controller 目前直接回傳 `ApiResponse<T>`，成功時 `data` 會放入對應的 Response DTO：

```json
{
  "statusCode": 200,
  "message": "Organizer account retrieved successfully",
  "messageDetails": null,
  "data": {}
}
```

錯誤訊息會透過 `ApiResponse.fail(...)` 統一轉成中文，讓前端可以直接用中文判斷錯誤。

詳細格式請看：

```text
api-response.md
```

## JWT 與登入狀態

- 登入成功後會回傳 JWT。
- JWT expiration 會與 DB `users.expired_time` 綁定。
- 同帳號重新登入後，舊 token 會因 `expired_time` 不一致而失效。
- `JwtAuthenticationFilter.protectedApis` 是需要 JWT 驗證 API 的清單來源。
- `isCurrentLoginSession` 只檢查目前 token 是否仍是 DB 中有效登入 session，不再額外檢查 `status = 'ACTIVE'`。

目前需要 JWT 驗證的 API：

```text
POST /api/auth/logout
POST /api/auth/google-bind
GET  /api/auth/me
POST /api/account/deactivate
GET  /api/vendor/account
GET  /api/vendor/stall-map/{applicationNo}
POST /api/stalls/select
GET  /api/organizer/account
GET  /api/organizer/accounts/{eventId}
GET  /api/organizer/equipment/search
GET  /api/organizer/equipment/{eventId}
GET  /api/organizer/stalls/search
GET  /api/organizer/stall/{eventId}
GET  /api/organizer/stall/{eventId}/{stallNo}
GET  /api/organizer/applications/search
GET  /api/organizer/applications/{id}
POST /api/organizer/applications/{id}/approve
POST /api/organizer/applications/{id}/reject
```

## API 清單

### 使用者與驗證 API

| Method | API                                     | Request DTO                     | JWT | 說明                                                |
| ------ | --------------------------------------- | ------------------------------- | --- | --------------------------------------------------- |
| GET    | `/usersall`                           | -                               | 否  | 查詢所有使用者。                                    |
| POST   | `/api/vendor/local-register`          | `LocalRegisterRequest`        | 否  | 攤主本地註冊。                                      |
| POST   | `/api/organizer/local-register`       | `LocalRegisterRequest`        | 否  | 主辦方本地註冊。                                    |
| POST   | `/api/vendor/google-register`         | `GoogleCredentialRequest`     | 否  | 攤主 Google 註冊。                                  |
| POST   | `/api/organizer/google-register`      | `GoogleCredentialRequest`     | 否  | 主辦方 Google 註冊。                                |
| POST   | `/api/vendor/local-login`             | `LocalLoginRequest`           | 否  | 攤主本地登入。                                      |
| POST   | `/api/organizer/local-login`          | `LocalLoginRequest`           | 否  | 主辦方本地登入。                                    |
| POST   | `/api/admin/local-login`              | `LocalLoginRequest`           | 否  | 管理員本地登入。                                    |
| POST   | `/api/vendor/google-login`            | `GoogleCredentialRequest`     | 否  | 攤主 Google 登入。                                  |
| POST   | `/api/organizer/google-login`         | `GoogleCredentialRequest`     | 否  | 主辦方 Google 登入。                                |
| POST   | `/api/auth/google-bind`               | `GoogleCredentialRequest`     | 是  | 綁定目前登入帳號與 Google。                         |
| POST   | `/api/auth/createAccount/emailVerify` | `EmailVerificationRequest`    | 否  | 註冊 Email 驗證。                                   |
| POST   | `/api/auth/resetPassword/request`     | `RequestPasswordResetRequest` | 否  | 申請重設密碼驗證碼。                                |
| POST   | `/api/auth/resetPassword/emailVerify` | `EmailVerificationRequest`    | 否  | 驗證重設密碼 Email 驗證碼，成功後回傳 reset token。 |
| POST   | `/api/auth/resetPassword/reset`       | `ResetPasswordRequest`        | 否  | 優先使用登入 token 更新密碼；未登入時使用 reset token 重設密碼。 |
| POST   | `/api/auth/logout`                    | -                               | 是  | 登出。                                              |
| GET    | `/api/auth/me`                        | -                               | 是  | 取得目前登入使用者資料。                            |
| POST   | `/api/account/deactivate`             | -                               | 是  | 停用目前登入帳號。                                  |

### 攤主與攤位 API

| Method | API                                       | Request DTO               | JWT | 說明                                                                                 |
| ------ | ----------------------------------------- | ------------------------- | --- | ------------------------------------------------------------------------------------ |
| POST   | `/api/stalls/select`                    | `StallSelectionRequest` | 是  | 依`applicationNo` 與 `selections[]` 一次送出該申請單所有報名日期的選位。         |
| GET    | `/api/eventsMap/{eventId}/stallsStatus` | -                         | 否  | 公開查詢活動指定日期攤位狀態；未帶日期時預設活動第一天。                             |
| GET    | `/api/vendor/account`                   | -                         | 是  | 取得目前登入攤主資料。                                                               |
| GET    | `/api/vendor/stall-map/{applicationNo}` | -                         | 是  | 查詢攤主自己的申請單選位地圖，可用`applyDate` 切換目前查看日期，並回傳報名日期數。 |

### 主辦方 API

| Method | API                                                     | Request              | JWT | 說明                                                             |
| ------ | ------------------------------------------------------- | -------------------- | --- | ---------------------------------------------------------------- |
| GET    | `/api/organizer/account`                              | Authorization header | 是  | 取得目前登入主辦方資料。                                         |
| GET    | `/api/organizer/accounts/search`                     | Query params         | 是  | 查詢主辦方帳務活動列表，可依活動名稱、狀態與活動日期篩選。       |
| GET    | `/api/organizer/accounts/{eventId}`                  | Query params         | 是  | 查詢活動帳務詳情，可依帳務狀態篩選付款明細。                     |
| GET    | `/api/organizer/accounts/{eventId}/export`           | Query params         | 是  | 匯出活動帳務 Excel 報表，可用 `status` 篩選付款明細。            |
| GET    | `/api/organizer/equipment/search`                   | Query params         | 是  | 查詢主辦方設備租借活動列表，可依活動名稱、狀態與活動日期篩選。 |
| GET    | `/api/organizer/equipment/{eventId}`                | Authorization header | 是  | 查詢主辦方活動設備、用電、租借統計與管理列表。                 |
| GET    | `/api/organizer/equipment/{eventId}/export`          | Authorization header | 是  | 匯出活動設備 Excel 報表。                                       |
| GET    | `/api/organizer/applications/search`                  | Authorization header | 是  | 查詢目前主辦方 published 活動的全部申請資料，依申請時間倒序。    |
| GET    | `/api/organizer/applications/{id}`                    | Authorization header | 是  | 查詢主辦方申請明細。                                             |
| GET    | `/api/organizer/stalls/search`                       | Query params         | 是  | 查詢主辦方攤位管理活動列表。                                     |
| GET    | `/api/organizer/stall/{eventId}`                     | Query params         | 是  | 查詢主辦方活動指定日期的攤位選位狀況，可依關鍵字與選位狀態篩選。 |
| GET    | `/api/organizer/stall/{eventId}/{stallNo}`           | Query params         | 是  | 查詢主辦方活動指定日期單一攤位的攤主與申請資訊。                 |
| POST   | `/api/organizer/applications/{id}/approve`            | Authorization header | 是  | 通過主辦方報名審核。                                             |
| POST   | `/api/organizer/applications/{id}/reject`             | Authorization header | 是  | 退回主辦方報名審核，可填寫退件原因。                             |

`GET /api/organizer/applications/{id}` 目前主要回傳區塊：

| 區塊                  | 說明                                                                                             |
| --------------------- | ------------------------------------------------------------------------------------------------ |
| `application`       | 申請 ID、申請編號與後端計算後的申請狀態。                                                        |
| `event`             | 活動名稱、活動狀態、活動日期與地址。                                                             |
| `vendor`            | 攤主聯絡資訊。                                                                                   |
| `brand`             | 品牌名稱、類別與描述。                                                                           |
| `applicationdetail` | 報名時段、攤位尺寸、攤位區域、攤位類別、車牌、申請備註與審核備註。                               |
| `stall`             | 每個報名日期的攤位選擇列，未選位也會回傳日期與`未選擇`。                                       |
| `fee`               | 付款狀態、付款方式、付款編號與付款金額。                                                         |
| `feedetail`         | 報名費、設備租借費、額外電費、保證金、總計。                                                     |
| `equipmentRentals`  | `freeEquipments`、`freeBasicPower`、`rentalEquipments`、`extraPower` 四區設備/用電資訊。 |
| `status`            | 固定狀態流清單與各節點時間。                                                                     |

`GET /api/organizer/equipment/{eventId}` 目前主要回傳區塊：

| 區塊 | 說明 |
| --- | --- |
| `event` | 活動名稱、狀態、活動時間、地點與地址。 |
| `eventEquipments` | 一般設備提供狀況；同一 `item_type + equipment_group_key` 的免費與付費設備會合併為一列，`dailyRentableQuantity` 為免費庫存加付費庫存總數。 |
| `basicPowers` | 免費基本用電資訊，包含電壓與免費瓦數。 |
| `extraPowers` | 付費額外用電資訊；`perStallProvidedQuantity` 為每攤可提供組數，`availableGroupQuantity` 為此用電方案可提供組數。 |
| `equipmentRentalStatistics` | 一般設備租借統計與剩餘數量；同一設備品項的免費與付費設定會合併統計。 |
| `extraPowerApplicationStatistics` | 額外用電申請數量統計。 |
| `vehicleRegistrationStatistics` | 車牌已登記與未登記統計。 |
| `equipmentRentalManagement` | 各攤商一般設備租借管理列表。 |
| `extraPowerManagement` | 各攤商額外用電管理列表。 |
| `vehicleManagement` | 已登記車牌的攤商列表。 |

`GET /api/organizer/accounts/{eventId}` 目前主要回傳區塊：

| 區塊 | 說明 |
| --- | --- |
| `event` | 活動圖片、名稱、狀態、日期、地點、攤位總數與已付款攤位數。 |
| `summary` | 收款總額、退款總額、已退款保證金、未退款保證金與實收總額。 |
| `statistics` | 付款、退款、保證金統計。 |
| `payments` | 付款明細；可用 `status` 篩選 `付款成功`、`退款處理中`、`退款申請中`、`已退款`、`已取消`。 |

`payments` 每列包含 `paymentNo`、`brandName`、`paidAt`、`paymentAmount`、`refundAmount`、`depositStatus`、`accountingStatus`。`refundAmount` 只代表已完成退款金額，退款申請中與退款處理中會回 `0`。

### 主辦方報表匯出 API

報表 API 會直接回傳 Excel 附件，不包 `ApiResponse` JSON。請前端用 blob/arraybuffer 接收，並依 `Content-Disposition` 取得檔名。

| Method | API | Query params | JWT | 成功回應 |
| --- | --- | --- | --- | --- |
| GET | `/api/organizer/accounts/{eventId}/export` | `status` 選填，可篩選 `付款成功`、`退款處理中`、`退款申請中`、`已退款`、`已取消` | 是 | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| GET | `/api/organizer/equipment/{eventId}/export` | - | 是 | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |

成功下載時，後端會回傳：

| Header | 說明 |
| --- | --- |
| `Content-Type` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| `Content-Disposition` | `attachment; filename*=UTF-8''...xlsx`，檔名會依報表類型與活動名稱產生。 |
| `Content-Length` | Excel byte 長度。 |

檔名規則：

| 報表 | 檔名格式 |
| --- | --- |
| 帳務報表 | `account-report-{活動名稱或eventId}.xlsx` |
| 設備報表 | `equipment-report-{活動名稱或eventId}.xlsx` |

錯誤回應分兩種：JWT 驗證失敗會回 `401 Unauthorized` 與 `ApiResponse` JSON，例如 `Authorization token is required`、`Invalid or expired token`、`Session expired`；通過驗證後若查無活動或參數錯誤，匯出 API 會回 `400 Bad Request` 與純文字錯誤訊息，例如 `Event id is required`、`Event not found`。

帳務報表工作表：

| 工作表 | 內容 |
| --- | --- |
| `活動資訊` | 活動 ID、活動名稱、發布狀態、狀態文字、狀態說明、活動日期、地點、地址、總攤位數、已付款攤位數。 |
| `帳務摘要` | 收款總額、退款總額、已退/未退保證金總額、實收總額、付款/退款/保證金統計。 |
| `付款明細` | 付款編號、品牌名稱、付款時間、付款金額、退款金額、保證金狀態、帳務狀態。 |

設備報表工作表：

| 工作表 | 內容 |
| --- | --- |
| `活動資訊` | 活動 ID、活動名稱、狀態、狀態說明、活動時間、地點、地址。 |
| `活動設備` | 設備名稱、設備類型、單位、免費提供數量、付費租借上限、每日可租借總數、租金與租借狀態。 |
| `基本用電` | 用電名稱、電壓、免費瓦數。 |
| `加購用電` | 用電名稱、用電方案、每攤可提供組數、可提供組數。 |
| `設備租借統計` | 設備名稱、已租借數量、可租借數量、剩餘數量、租借/總計。 |
| `設備租借管理` | 攤位編號、品牌名稱與各設備租借數量；設備欄位會依活動實際設備動態產生。 |
| `加購用電管理` | 攤位編號、品牌名稱、用電方案。 |
| `車輛管理` | 攤位編號、品牌名稱、聯絡人、車牌號碼。 |

報表產出流程：

1. 前端帶主辦方 JWT 呼叫 `/api/organizer/accounts/{eventId}/export` 或 `/api/organizer/equipment/{eventId}/export`。
2. `JwtAuthenticationFilter` 先驗證該下載 API 是否有合法 Authorization token。
3. `OrganizerController` 收到請求後呼叫 `OrganizerService` 的匯出方法。
4. Service 先取得目前登入主辦方，再用 `eventId` 查詢該主辦方名下活動；若查不到會回 `Event not found`。
5. 帳務報表會重用帳務詳情查詢邏輯，組出 `event`、`summary`、`statistics`、`payments`；若有帶 `status`，付款明細會先依帳務狀態篩選。
6. 設備報表會重用設備詳情查詢邏輯，組出活動資訊、設備設定、用電設定、租借統計與各管理列表。
7. 後端使用 Apache POI `XSSFWorkbook` 建立 `.xlsx`，每個資料區塊寫成獨立工作表，表頭加粗、凍結第一列並自動調整欄寬。
8. Service 回傳 `ReportExport`，Controller 轉成 Excel 附件回應，前端即可觸發下載。

## 文件維護規則

- README 的更新紀錄請放在「更新日誌」底下，並以日期分區。
- 新增或調整 API 時，同步更新 `README.md`、`api-response.md`、`swagger.md`。
- 新增需要 JWT 驗證的 API 時，同步更新 `JwtAuthenticationFilter.protectedApis`。
- 新增 request body 時，請建立或更新 `dto/request`。
- 新增 response data 時，請建立或更新 `dto/response`。
- 錯誤訊息請透過 `ApiResponse.fail(...)` 回傳，讓前端收到中文錯誤訊息。

## Google 登入 / 註冊輔助測試頁面

```text
http://localhost:8081/test.html
http://localhost:8081/test_2.html
```

## 2026-07-01 simon branch 補充更新

- `POST /api/organizer/applications/{id}/approve` 與 `POST /api/organizer/applications/{id}/reject` 拆分主辦方報名審核 API；申請 `id` 改由 path params 傳入，通過不需 body，退件 body 可帶 `reviewNote`、`reviewNoteDetail`。
- 退件原因在不變更 SQL 結構下以 JSON 字串存入 `event_applications.review_note`，格式為 `{"reviewNote":"...","reviewNoteDetail":"..."}`。
- `GET /api/organizer/applications/{id}` 會解析 `event_applications.review_note`，並在 `applicationdetail.reviewNote` 與 `applicationdetail.reviewNoteDetail` 回傳；若遇到舊純文字資料，會以 `reviewNote` 回傳並讓 `reviewNoteDetail` 為 `null`。
