# Market Day API

Market Day 是小集日市集平台的 Spring Boot API 專案，提供帳號登入註冊、攤主資料、主辦資料、活動查詢、攤位選位、主辦後台管理、設備統計與帳務匯出等功能。

最後更新：2026-07-14

## 更新紀錄

### 2026-07-14

#### simon branch

- 新增共用正式圖片儲存 API：`POST /api/images`，支援攤主大頭照、攤主封面、商品圖片、活動封面與活動地圖五種用途。
- 圖片 API 依用途使用 `productId` 或 `eventId` 綁定目標資料，並驗證 JWT、帳號角色及資料所有權；攤主大頭照與封面不需提供目標 ID。
- 圖片檔案儲存於可設定的 `images` 目錄，透過 `/images/**` 提供前端存取；新增圖片目錄、公開網址、5 MB 上傳限制與 CORS 設定。
- 圖片上傳會檢查實際檔案內容；一般圖片僅接受 JPG、PNG，活動地圖另接受 PDF，儲存或資料綁定失敗時不會更新圖片 URL。
- `POST /api/vendor/stall/save` 改為一次接收品牌資料與完整商品清單，商品最多 3 筆；既有商品依 ID 更新、新商品新增、未送出的既有商品刪除。
- 攤主品牌資料儲存不再修改大頭照與封面 URL，避免未重新上傳圖片時將原圖片覆蓋為空值；圖片統一由 `POST /api/images` 更新。
- 移除獨立的攤主商品新增、編輯及刪除 API：`/api/vendor/stall/addproduct`、`/api/vendor/stall/edituct/{id}`、`/api/vendor/stall/deleteproduct/{id}`。
- `GET /api/brands/search` 與 `GET /api/brands/{id}` 改為回傳品牌目前刊登的全部商品，不再限制特色商品、商品狀態或最多 3 筆。
- 補上圖片上傳大小超限及圖片／商品儲存驗證的統一錯誤回應，並同步更新 Swagger 說明。

### 2026-07-11

#### simon branch

- 新增品牌展示 API：`GET /api/brands/search`、`GET /api/brands/{id}`、`GET /api/brands/scroll-options`，可用於前台品牌列表、品牌詳情與篩選選項。
- 新增主辦 profile API：`GET /api/organizer/profile/load`、`POST /api/organizer/profile/save`。
- 主辦 profile 的服務時間改為分開輸入與輸出：`serviceStartTime`、`serviceEndTime`。
- 主辦 profile 的地址輸出只回傳原本儲存的 `address`，不再自動組合 `city + district + address`。
- 新增 `TaiwanAddressService`，用來驗證台灣縣市與行政區。
- `POST /api/organizer/profile/save` 增加欄位輸入限制與對應錯誤訊息。
- `GET /api/organizer/equipment/{eventId}` 新增 `equipmentOverview`，包含報名攤數、基本設備數、基本用電數、設備租借數、額外用電數、車牌登記數。
- `GET /api/organizer/stalls/search` 新增 `availableStallCount`、`selectedStallCount`。
- 新增攤主資料 API：`GET /api/vendor/stall/load`、`POST /api/vendor/stall/save`、`POST /api/vendor/stall/addproduct`、`POST /api/vendor/stall/edituct/{id}`、`POST /api/vendor/stall/deleteproduct/{id}`。
- `GET /api/organizer/stall/{eventId}` 的 `event` 區塊新增 `selectedStallCount`、`availableStallCount`，可顯示攤位總數、已選擇攤位數與可選攤位數。
- `GET /api/organizer/stall/{eventId}` 的 `keyword` 會查詢攤位編號 `stallNo` 與已選攤位的品牌名稱 `selectedVendor.name`。
- `GET /api/organizer/stall/{eventId}/{stallNo}` 的攤位詳情 `vendor` 區塊新增 `brandType`。
- 移除 `GET /api/organizer/account`，主辦個人資料改由 profile API 讀取與儲存。
- 同步更新 Swagger 與 API response 文件。

### 2026-07-10

#### yushuan branch

- 新增藍新金流付款流程 API，提供攤主於報名審核通過後建立付款、前往藍新付款頁、接收藍新付款通知、查詢本地付款狀態與補查藍新交易狀態等功能。
- `POST /api/vendor/payments/newebpay` 新增攤主建立藍新付款 API。前端點擊付款按鈕後呼叫此 API，後端會檢查 JWT token、攤主身分、申請單是否存在、申請單是否屬於目前攤主、審核狀態是否通過、付款狀態是否可付款，以及付款金額是否有效；檢查通過後建立本地付款資料並回傳藍新付款表單所需欄位。
- `POST /api/newebpay/notify` 新增藍新背景付款通知 API。藍新付款完成後會呼叫此 API，後端負責驗證 TradeSha、解密 TradeInfo、比對付款金額，並於付款成功時更新本地付款狀態為 `PAID`，同步更新申請單付款狀態。
- `POST /api/newebpay/return` 新增藍新付款完成返回 API。使用者完成藍新付款後會被導回此 API，後端接收付款返回資料後導回前端付款結果頁；付款成功狀態仍以 `POST /api/newebpay/notify` 為主要依據。
- `GET /api/newebpay/return` 新增付款返回備用/測試入口，供瀏覽器直接開啟 ReturnURL 時導回前端付款結果頁。
- `GET /api/vendor/payments/{applicationNo}/status` 新增攤主查詢本地付款狀態 API。前端可依申請單號查詢目前本地付款狀態，用於付款頁面刷新、付款結果頁確認或付款按鈕狀態判斷。
- `POST /api/vendor/payments/{applicationNo}/newebpay-query` 新增藍新交易狀態補查 API。此 API 用於向藍新查詢指定申請單對應交易狀態，通常用於除錯、付款通知未收到或需要補正本地付款狀態時使用。
- 新增 `NewebPayProperties` 讀取藍新金流設定，包含商店代號、HashKey、HashIV、版本、付款網址、NotifyURL、ReturnURL 與查詢網址等設定。
- 新增 `NewebPayService` 處理藍新金流流程，包含建立付款資料、TradeInfo 加密、TradeSha 產生、Notify/Return 解密驗章、付款狀態更新與交易狀態補查。
- 新增 `PaymentRepository` 操作金流相關資料，包含查詢申請單付款資料、建立付款紀錄、更新付款狀態與查詢付款狀態。
- 新增金流相關 DTO：`VendorPaymentRequest`、`NewebPayPaymentResponse`、`NewebPayQueryResponse`、`PaymentStatusResponse`。

### 2026-07-09

#### simon branch

- `GET /api/organizer/equipment/search` 新增主辦設備列表查詢。
- `GET /api/organizer/equipment/{eventId}` 新增設備租借管理、額外用電管理、車牌管理與統計資料。
- `GET /api/organizer/equipment/{eventId}/export` 新增設備資料 Excel 匯出。
- 主辦列表 API 支援 `page`、`pageSize` 分頁參數。
- `GET /api/organizer/accounts/{eventId}` 的帳務列表支援 `paymentPage`、`paymentPageSize`。

### 2026-07-07

#### simon branch

- `GET /api/organizer/applications/{id}` 新增活動、報名、費用、設備、狀態時間軸等詳細資料。
- 主辦攤位地圖 API 改為 `GET /api/organizer/stall/{eventId}`。
- 主辦攤位詳情 API 改為 `GET /api/organizer/stall/{eventId}/{stallNo}`。
- `GET /api/organizer/stall/{eventId}` 支援 `applyDate`、`keyword`、`status` 查詢。
- `GET /api/organizer/accounts/search` 新增主辦帳務活動列表查詢。
- `GET /api/organizer/accounts/{eventId}` 新增帳務明細。
- `GET /api/organizer/accounts/{eventId}/export` 新增帳務 Excel 匯出。

### 2026-07-02

#### simon branch

- `POST /api/stalls/select` 改為使用 `applicationNo` 與 `selections` 選位。
- `GET /api/vendor/stall-map/{applicationNo}` 新增 `applyDate` 查詢與多日期選位資訊。
- 新增公開攤位狀態 API：`GET /api/eventsMap/{eventId}/stallsStatus`。

### 2026-06-29

#### simon branch

- 註冊、登入與 Google OAuth 流程調整。
- `POST /api/auth/google-bind` 支援本地帳號綁定 Google。
- `GET /api/auth/me` 新增目前登入使用者資料。
- `POST /api/account/deactivate` 可停用目前帳號。
- `users` 新增 `name`、`phone`，並同步調整 profile 資料。
- `market_events` 時間欄位改為 `DATETIME2(0)`。

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
- Apache POI

## 專案結構

```text
demo/
├─ pom.xml
├─ mvnw / mvnw.cmd
├─ run-local.cmd
├─ README.md
├─ api-response.md
├─ swagger.md
└─ src/main/
   ├─ java/com/example/demo/
   │  ├─ Config/
   │  ├─ Controller/
   │  ├─ Filter/
   │  ├─ Repository/
   │  ├─ Service/
   │  ├─ dto/
   │  └─ swagger/
   └─ resources/
      ├─ application.properties
      └─ static/
```

## 環境變數

`run-local.cmd` 會設定本機開發常用環境變數並啟動 Spring Boot。

| 變數 | 說明 |
| --- | --- |
| `DB_URL` | SQL Server JDBC URL |
| `DB_USERNAME` | SQL Server 帳號 |
| `DB_PASSWORD` | SQL Server 密碼 |
| `GOOGLE_CLIENT_ID` | Google OAuth Client ID |
| `MAIL_USERNAME` | 寄信帳號 |
| `MAIL_PASSWORD` | 寄信密碼或 App Password |
| `JWT_SECRET` | JWT secret |
| `JWT_EXPIRATION_MS` | JWT 有效時間 |
| `ADMIN_EMAIL` | 預設管理員 email |
| `ADMIN_PASSWORD` | 預設管理員密碼 |

## 啟動與測試

啟動：

```powershell
cd demo
.\mvnw.cmd spring-boot:run
```

測試：

```powershell
cd demo
.\mvnw.cmd test
```

Swagger UI：

```text
http://localhost:8081/swagger-ui/marketDay/index.html
```

OpenAPI JSON：

```text
http://localhost:8081/v3/api-docs
```

## API Response 格式

所有 Controller 預設回傳 `ApiResponse<T>`。

成功範例：

```json
{
  "statusCode": 200,
  "message": "Organizer profile loaded successfully",
  "messageDetails": null,
  "data": {}
}
```

失敗範例：

```json
{
  "statusCode": 400,
  "message": "Validation failed",
  "messageDetails": "city is invalid",
  "data": null
}
```

## JWT 保護

需要登入的 API 必須帶：

```text
Authorization: Bearer {token}
```

受 JWT 保護的主要 API：

- `POST /api/auth/logout`
- `POST /api/auth/google-bind`
- `GET /api/auth/me`
- `POST /api/account/deactivate`
- `GET /api/vendor/account`
- `GET /api/vendor/stall/load`
- `POST /api/vendor/stall/save`
- `POST /api/vendor/stall/addproduct`
- `POST /api/vendor/stall/edituct/{id}`
- `POST /api/vendor/stall/deleteproduct/{id}`
- `GET /api/vendor/stall-map/{applicationNo}`
- `POST /api/stalls/select`
- `GET /api/organizer/profile/load`
- `POST /api/organizer/profile/save`
- `GET /api/organizer/accounts/{eventId}`
- `GET /api/organizer/accounts/{eventId}/export`
- `GET /api/organizer/stalls/search`
- `GET /api/organizer/equipment/search`
- `GET /api/organizer/equipment/{eventId}`
- `GET /api/organizer/equipment/{eventId}/export`
- `GET /api/organizer/stall/{eventId}`
- `GET /api/organizer/stall/{eventId}/{stallNo}`

## 公開 API

| Method | API | 說明 |
| --- | --- | --- |
| GET | `/api/eventsMap/{eventId}/stallsStatus` | 公開攤位狀態地圖 |
| GET | `/api/brands/scroll-options` | 品牌列表篩選選項 |
| GET | `/api/brands/search` | 品牌列表查詢 |
| GET | `/api/brands/{id}` | 品牌詳情 |
| POST | `/api/markets/search` | 市集列表查詢 |
| GET | `/api/markets/{id}` | 市集詳情 |

## 帳號 API

| Method | API | 說明 |
| --- | --- | --- |
| POST | `/api/vendor/local-register` | 攤主本地註冊 |
| POST | `/api/organizer/local-register` | 主辦本地註冊 |
| POST | `/api/vendor/google-register` | 攤主 Google 註冊 |
| POST | `/api/organizer/google-register` | 主辦 Google 註冊 |
| POST | `/api/vendor/local-login` | 攤主本地登入 |
| POST | `/api/organizer/local-login` | 主辦本地登入 |
| POST | `/api/admin/local-login` | 管理員登入 |
| POST | `/api/vendor/google-login` | 攤主 Google 登入 |
| POST | `/api/organizer/google-login` | 主辦 Google 登入 |
| POST | `/api/auth/google-bind` | 登入後綁定 Google |
| POST | `/api/auth/createAccount/emailVerify` | 註冊信箱驗證 |
| POST | `/api/auth/resetPassword/request` | 申請重設密碼 |
| POST | `/api/auth/resetPassword/emailVerify` | 重設密碼信箱驗證 |
| POST | `/api/auth/resetPassword/reset` | 重設密碼 |
| POST | `/api/auth/logout` | 登出 |
| GET | `/api/auth/me` | 目前登入使用者 |
| POST | `/api/account/deactivate` | 停用目前帳號 |

## 攤主金流 API

| Method | API | Request DTO | JWT | 說明 |
| ------ | --- | ----------- | --- | ---- |
| POST | `/api/vendor/payments/newebpay` | `VendorPaymentRequest` | 是 | 攤主建立藍新付款資料，後端檢查申請單狀態並回傳藍新付款表單欄位。 |
| GET | `/api/vendor/payments/{applicationNo}/status` | - | 是 | 查詢指定申請單的本地付款狀態。 |
| POST | `/api/vendor/payments/{applicationNo}/newebpay-query` | - | 是 | 向藍新補查交易狀態，用於除錯或 Notify 未收到時補正狀態。 |

## 藍新金流回呼 API

| Method | API | Request | JWT | 說明 |
| ------ | --- | ------- | --- | ---- |
| POST | `/api/newebpay/notify` | 藍新回傳表單資料 | 否 | 藍新背景通知付款結果，後端驗章、解密、比對金額並更新付款狀態。 |
| POST | `/api/newebpay/return` | 藍新回傳表單資料 | 否 | 使用者付款完成後由藍新導回，後端處理後導回前端付款結果頁。 |
| GET | `/api/newebpay/return` | Query params | 否 | ReturnURL 備用/測試入口，供瀏覽器直接開啟時導回前端。 |

## 藍新付款流程

```text
前端點付款按鈕
  ↓
POST /api/vendor/payments/newebpay
  ↓
後端檢查 token、攤主身分、申請單、歸屬、審核狀態、付款狀態與金額
  ↓
後端建立本地付款紀錄並回傳藍新付款表單資料
  ↓
前端 form POST 到藍新付款頁
  ↓
藍新付款完成
  ↓
POST /api/newebpay/notify
  ↓
後端驗章、解密、比對金額，更新本地付款狀態為 PAID
  ↓
POST /api/newebpay/return
  ↓
藍新導使用者回系統，後端再導回前端付款結果頁
```

## 攤主 API

| Method | API | 說明 |
| --- | --- | --- |
| GET | `/api/vendor/account` | 攤主帳號資料 |
| GET | `/api/vendor/stall/load` | 讀取攤主品牌與商品資料 |
| POST | `/api/vendor/stall/save` | 儲存攤主品牌基本資料 |
| POST | `/api/vendor/stall/addproduct` | 新增商品 |
| POST | `/api/vendor/stall/edituct/{id}` | 編輯商品 |
| POST | `/api/vendor/stall/deleteproduct/{id}` | 刪除商品 |
| GET | `/api/vendor/stall-map/{applicationNo}` | 攤主選位地圖 |
| POST | `/api/stalls/select` | 攤主送出選位 |

### 攤主資料欄位

`GET /api/vendor/stall/load` 與 `POST /api/vendor/stall/save` 主要處理：

- 品牌名稱
- 負責人
- 聯絡電話
- 聯絡電子郵件
- 縣市
- 地區
- 地址
- Instagram
- Facebook
- 官方網站
- 品牌大頭照
- 品牌封面照
- 品牌簡介
- 品牌介紹
- 品牌類型
- 品牌商品

商品欄位：

- 商品圖片
- 商品名稱
- 商品簡述
- 商品金額

`POST /api/vendor/stall/save` 不會用商品列表覆蓋舊商品。商品新增、編輯、刪除分別使用 `addproduct`、`edituct/{id}`、`deleteproduct/{id}`。

## 主辦 API

| Method | API | 說明 |
| --- | --- | --- |
| GET | `/api/organizer/profile/load` | 讀取主辦基本資料 |
| POST | `/api/organizer/profile/save` | 儲存主辦基本資料 |
| GET | `/api/organizer/applications/search` | 報名列表查詢 |
| GET | `/api/organizer/applications/{id}` | 報名詳情 |
| POST | `/api/organizer/applications/{id}/approve` | 審核通過 |
| POST | `/api/organizer/applications/{id}/reject` | 退回或拒絕 |
| GET | `/api/organizer/stalls/search` | 攤位管理活動列表 |
| GET | `/api/organizer/stall/{eventId}` | 主辦攤位地圖 |
| GET | `/api/organizer/stall/{eventId}/{stallNo}` | 主辦攤位詳情 |
| GET | `/api/organizer/equipment/search` | 設備管理活動列表 |
| GET | `/api/organizer/equipment/{eventId}` | 設備管理詳情 |
| GET | `/api/organizer/equipment/{eventId}/export` | 設備資料匯出 |
| GET | `/api/organizer/accounts/search` | 帳務管理活動列表 |
| GET | `/api/organizer/accounts/{eventId}` | 帳務管理詳情 |
| GET | `/api/organizer/accounts/{eventId}/export` | 帳務資料匯出 |

### 主辦 profile 欄位

`GET /api/organizer/profile/load` 輸出與 `POST /api/organizer/profile/save` 輸入包含：

- `organizerName`
- `contactName`
- `contactPhone`
- `contactEmail`
- `city`
- `district`
- `address`
- `companyName`
- `taxId`
- `serviceDays`
- `serviceStartTime`
- `serviceEndTime`

時間欄位分開回傳，不合併成一個字串。地址欄位只回傳資料庫中的 `address`，不自動組合縣市與地區。

### 主辦 profile 輸入限制

| 欄位 | 規則 |
| --- | --- |
| `organizerName` | 必填，長度不得超過資料庫欄位限制 |
| `contactName` | 必填 |
| `contactPhone` | 必填，需符合電話格式 |
| `contactEmail` | 必填，需符合 email 格式 |
| `city` | 必填，需存在於 `TaiwanAddressService` 的台灣縣市清單 |
| `district` | 必填，需存在於指定縣市的行政區清單 |
| `address` | 必填 |
| `companyName` | 必填 |
| `taxId` | 若填寫需符合統一編號格式 |
| `serviceDays` | 必填 |
| `serviceStartTime` | 必填，時間格式 |
| `serviceEndTime` | 必填，時間格式，需晚於開始時間 |

## 主辦攤位地圖

`GET /api/organizer/stall/{eventId}`

Query params：

| 參數 | 說明 |
| --- | --- |
| `applyDate` | 活動日期，不填則使用預設日期 |
| `keyword` | 查詢攤位編號 `stallNo` 或已選攤位的品牌名稱 `selectedVendor.name` |
| `status` | 篩選攤位狀態 |

回傳重點：

```json
{
  "event": {
    "eventId": 1,
    "title": "活動名稱",
    "locationName": "活動地點",
    "startAt": "2026-07-18T14:00:00",
    "endAt": "2026-07-19T18:00:00",
    "totalStallCount": 150,
    "selectedStallCount": 12,
    "availableStallCount": 138
  },
  "applyDates": [],
  "currentApplyDate": "2026-07-18",
  "zones": []
}
```

攤位資料中的 `status` 會回傳顯示用狀態，例如可選擇、已選擇、不可選。

`GET /api/organizer/stall/{eventId}/{stallNo}`

回傳單一攤位資料，包含攤位編號、區域、尺寸、狀態、選位時間與攤主品牌資訊。`vendor.brandType` 會回傳品牌類型。

## 主辦設備管理

`GET /api/organizer/equipment/{eventId}` 主要回傳：

- `event`
- `equipmentOverview`
- `eventEquipments`
- `equipmentRentalStatistics`
- `equipmentRentalManagement`
- `extraPowerManagement`
- `vehicleManagement`

`equipmentOverview` 包含：

- 報名攤數
- 基本設備數
- 基本用電數
- 設備租借數
- 額外用電數
- 車牌登記數

## 主辦帳務管理

`GET /api/organizer/accounts/{eventId}` 支援：

- `status`
- `paymentPage`
- `paymentPageSize`

回傳內容包含：

- `event`
- `summary`
- `statistics`
- `payments`

`GET /api/organizer/accounts/{eventId}/export` 會輸出 Excel：

```text
account-report-{eventId}.xlsx
```

## 文件

- Swagger 補充：`swagger.md`
- API response 補充：`api-response.md`
- SQL 文件：`../sql/README.md`

## 注意事項

- `GET /api/organizer/account` 已移除，請改用 `GET /api/organizer/profile/load`。
- 商品刪除目前使用 `POST /api/vendor/stall/deleteproduct/{id}`。
- 商品編輯 API 路徑目前維持既有拼字：`POST /api/vendor/stall/edituct/{id}`。
- README 不記錄測試資料腳本內容，正式 API 行為以 Controller、Service、Swagger 與本文件為準。
