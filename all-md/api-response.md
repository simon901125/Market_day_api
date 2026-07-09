# API Response 與 DTO 架構

更新日期：2026-06-29

目前 `demo` 的 Controller 直接回傳 `ApiResponse<T>`，其中 `T` 會是對應的 Response DTO。
Request body 使用 `dto/request` 內的 Request DTO，Response data 使用 `dto/response` 內的 Response DTO。

## 統一回傳格式

所有 API 回傳格式如下：

```json
{
  "statusCode": 200,
  "message": "Login successful",
  "messageDetails": null,
  "data": {}
}
```

## 2026-07-01 更新：Organizer 報名審核 API

主辦方報名審核拆成兩支 API：

```http
POST /api/organizer/applications/{id}/approve
POST /api/organizer/applications/{id}/reject
```

### 審核通過

`POST /api/organizer/applications/{id}/approve`

- 需要 `Authorization` header。
- 不需要 request body。
- 成功後會將 `event_applications.review_status` 更新為 `APPROVED`。
- `status_logs` 會記錄 `target_type = EVENT_APPLICATION`、`status_field = event_applications.review_status`、`new_status = APPROVED`。

### 審核不通過

`POST /api/organizer/applications/{id}/reject`

- 需要 `Authorization` header。
- `reviewNote`、`reviewNoteDetail` 皆非必填。
- 成功後會將 `event_applications.review_status` 更新為 `REJECTED`。
- 不變更 SQL 結構，退件原因會以 JSON 字串存入 `event_applications.review_note`。

Request body:

```json
{
  "reviewNote": "資料不完整",
  "reviewNoteDetail": "請補上商品照片"
}
```

DB `event_applications.review_note` 儲存格式：

```json
{"reviewNote":"資料不完整","reviewNoteDetail":"請補上商品照片"}
```

Response data:

```json
{
  "applicationId": 101,
  "applicationNo": "T2-ORDER-3",
  "reviewStatus": "REJECTED",
  "reviewNote": "資料不完整",
  "reviewNoteDetail": "請補上商品照片"
}
```

`GET /api/organizer/applications/{id}` 會解析 `event_applications.review_note`，並在 `data.applicationdetail` 回傳：

```json
{
  "registrationPeriods": "2026-06-28 11:00-19:00 - 2026-06-29 11:00-19:00",
  "stallSize": "3x2.5",
  "stallZone": "A",
  "stallCategory": "Food",
  "vehicleNo": "T5-CAR-03",
  "applicantNote": "待付款 test application.",
  "reviewNote": "資料不完整",
  "reviewNoteDetail": "請補上商品照片"
}
```

若 `review_note` 是舊版純文字資料，API 會將純文字放在 `reviewNote`，並讓 `reviewNoteDetail` 為 `null`。

| 欄位 | 型別 | 說明 |
| --- | --- | --- |
| `statusCode` | number | 成功通常為 `200`，一般錯誤為 `400`，JWT 驗證失敗為 `401`。 |
| `message` | string | API 結果訊息。錯誤訊息會透過 `ApiResponse.fail(...)` 統一轉成中文。 |
| `messageDetails` | string / null | 目前多數 Controller 直接回傳 `ApiResponse<T>`，因此通常為 `null`。 |
| `data` | object / array / null | 成功時放 Response DTO；失敗時通常為 `null`。 |

## DTO 分區

| 類型 | 位置 | 用途 |
| --- | --- | --- |
| Request DTO | `src/main/java/com/example/demo/dto/request` | 接收 request body，例如登入、註冊、驗證碼、重設密碼、攤位選擇。 |
| Response DTO | `src/main/java/com/example/demo/dto/response` | API 成功時放入 `ApiResponse<T>.data` 的資料結構。 |
| API wrapper | `src/main/java/com/example/demo/dto/response/ApiResponse.java` | 統一包裝 `statusCode`、`message`、`messageDetails`、`data`。 |

目前常用 Response DTO 包含：

| Response DTO | 主要使用 API |
| --- | --- |
| `LoginResponse` | local / Google login |
| `LoginUserResponse` | `LoginResponse.user` |
| `UserProfileResponse` | `/api/auth/me` |
| `UserResponse` | `/usersall` |
| `VendorAccountResponse` | `/api/vendor/account` |
| `VendorStallMapResponse` | `/api/vendor/stall-map/{applicationNo}` |
| `OrganizerAccountResponse` | `/api/organizer/account` |
| `OrganizerAccountingSearchResponse` | `/api/organizer/accounts/search` |
| `MapBackedResponse` | `/api/organizer/accounts/{eventId}`、`/api/organizer/equipment/{eventId}` |
| `OrganizerApplicationSummaryResponse` | `/api/organizer/applications/search` |
| `OrganizerApplicationDetailResponse` | `/api/organizer/applications/{id}` |
| `EventStallStatusResponse` | `/api/eventsMap/{eventId}/stallsStatus` |
| `StallSelectionResponse` | `/api/stalls/select` |
| `PasswordResetVerificationResponse` | `/api/auth/resetPassword/emailVerify` |

## 登入成功範例

`POST /api/vendor/local-login`

```json
{
  "statusCode": 200,
  "message": "Login successful",
  "messageDetails": null,
  "data": {
    "token": "<JWT_TOKEN>",
    "user": {
      "email": "vendor@example.com",
      "name": "vendor1",
      "role": "VENDOR",
      "status": "ACTIVE",
      "isLogin": true
    }
  }
}
```

## 攤位選位 Response

`POST /api/stalls/select`

Request body 需要 `applicationNo` 與 `selections[]`，每筆選位包含 `applyDate` 與 `stallNo`；`eventId` 由後端依申請單查出。
`applicationNo` 與 `stallNo` 目前只檢查必填，不限制編號格式。

```json
{
  "applicationNo": "MD001",
  "selections": [
    {
      "applyDate": "2026-09-01",
      "stallNo": "A01"
    },
    {
      "applyDate": "2026-09-02",
      "stallNo": "A01"
    }
  ]
}
```

成功時 `data` 為 `StallSelectionResponse`：

```json
{
  "statusCode": 200,
  "message": "Stall selection successful",
  "messageDetails": null,
  "data": {
    "applicationNo": "MD001",
    "stallNo": "A01"
  }
}
```

錯誤訊息會拆分為申請單狀態問題與攤位選位機制問題。

申請單狀態問題：

- `找不到申請資料`
- `此申請不屬於目前登入帳號`
- `此申請已取消`
- `此申請尚在審核中`
- `此申請審核未通過`
- `此申請尚未通過審核`
- `此申請尚未付款`
- `此申請付款狀態不可選位`
- `此申請已完成選位`

攤位選位機制問題：

- `找不到攤位資料`
- `此攤位不可選擇`
- `此攤位已被選走`

並發搶位失敗時會回：

```json
{
  "statusCode": 400,
  "message": "此攤位已被選走",
  "messageDetails": null,
  "data": null
}
```

## 攤位地圖 Response

`GET /api/vendor/stall-map/{applicationNo}`

此 API 只允許目前登入攤主查詢自己的申請單，且申請單必須是 `待選位` 或已成功選位的有效狀態。已成功選位時可用來確認攤位資料。

```json
{
  "statusCode": 200,
  "message": "Vendor stall map retrieved successfully",
  "messageDetails": null,
  "data": {
    "application": {
      "applicationNo": "MD001",
      "applicationStatus": "報名完成",
      "vendorName": "vendor1 攤位",
      "selectedStallId": 123,
      "selectedStall": {
        "selectedStallId": 123,
        "stallNo": "A01",
        "zoneName": "A",
        "width": 3.00,
        "length": 3.00,
        "height": 2.50
      }
    },
    "event": {
      "eventTitle": "MD0101",
      "startAt": "2026-09-01T11:00:00",
      "endAt": "2026-09-03T19:00:00",
      "address": "台北市中正區市集路1-1號"
    },
    "stalls": []
  }
}
```

若申請單尚未進入可查看地圖的狀態，會回：

```json
{
  "statusCode": 400,
  "message": "此申請單目前不可查看攤位地圖",
  "messageDetails": null,
  "data": null
}
```

## Organizer 申請列表

`GET /api/organizer/applications/search`

目前此 API：

- 需要 `Authorization` header。
- 不接收 request body。
- 回傳目前登入主辦方的全部申請資料。
- 依 `event_applications.created_at DESC, event_applications.id DESC` 排序。
- `data` 型別為 `List<OrganizerApplicationSummaryResponse>`。

```json
{
  "statusCode": 200,
  "message": "Organizer applications retrieved successfully",
  "messageDetails": null,
  "data": [
    {
      "applicationId": 1,
      "applicationNo": "APP-MD0101-V01",
      "eventId": 1,
      "eventTitle": "MD0101",
      "eventTime": "2026-09-01 11:00 - 2026-09-03 19:00",
      "applyDates": "2026-09-01,2026-09-02",
      "vendorName": "vendor1",
      "vendorOwnerName": "vendor1",
      "brandType": "餐飲",
      "appliedAt": "2026-07-01T14:00:00",
      "applicationStatus": "報名完成"
    }
  ]
}
```

## Organizer 申請明細

`GET /api/organizer/applications/{id}`

需要 `Authorization` header。
`data` 型別為 `OrganizerApplicationDetailResponse`，主要包含：

| 區塊 | 說明 |
| --- | --- |
| `application` | 申請 ID、申請編號與後端計算後的申請狀態。 |
| `event` | 活動名稱、活動狀態、活動日期與地址。 |
| `vendor` | 攤主聯絡資料。 |
| `brand` | 品牌資料。 |
| `applicationdetail` | 報名時段、攤位尺寸、攤位區域、攤位類別、車牌、申請備註與審核備註。 |
| `stall` | 每個報名日期的攤位選擇列。 |
| `fee` | 付款狀態、付款方式、付款編號與付款金額。 |
| `feedetail` | 報名費、設備租借費、額外電費、保證金與總計。 |
| `equipmentRentals` | 免費設備、免費基本用電、租借設備、額外申請用電四區。 |
| `status` | 固定狀態流清單與各節點時間。 |

```json
{
  "statusCode": 200,
  "message": "主辦方申請詳情取得成功",
  "messageDetails": null,
  "data": {
    "application": {
      "applicationId": 1,
      "applicationNo": "T5-APP-06",
      "applicationStatus": "報名完成"
    },
    "event": {
      "eventTitle": "T5-APPLICATION-STATUS",
      "eventStatus": "已結束",
      "eventTime": "2026-06-28 - 2026-06-29",
      "address": "Taipei CityXinyi DistrictT5 Application Status Address 1"
    },
    "applicationdetail": {
      "registrationPeriods": "2026-06-28 11:00-19:00 - 2026-06-29 11:00-19:00",
      "stallSize": "3x2.5",
      "stallZone": "A",
      "stallCategory": "Food",
      "vehicleNo": "T5-CAR-06",
      "applicantNote": "報名完成 test application.",
      "reviewNote": null,
      "reviewNoteDetail": null
    },
    "stall": [
      {
        "applyDate": "2026-06-28",
        "stallNo": "A06",
        "zoneName": "A",
        "selectionStatus": "已選擇"
      }
    ],
    "fee": {
      "paymentStatus": "付款成功",
      "paymentMethod": "TEST",
      "paymentNo": "PAY-T5-APP-06",
      "paymentAmount": 5400
    },
    "feedetail": [
      { "item": "報名費", "content": "2天 (2026-06-28、2026-06-29)", "amount": 2400 },
      { "item": "設備租借費", "content": "帳篷租借*2、冷藏櫃租借*1", "amount": 2100 },
      { "item": "額外電費", "content": "110V / 500W*2", "amount": 600 },
      { "item": "保證金", "content": "保證金", "amount": 300 },
      { "item": "總計", "content": null, "amount": 5400 }
    ],
    "equipmentRentals": {
      "freeEquipments": [],
      "freeBasicPower": [],
      "rentalEquipments": [],
      "extraPower": []
    },
    "status": []
  }
}
```

## Organizer 帳務詳情

`GET /api/organizer/accounts/{eventId}`

需要 `Authorization` header。可用 `status` query param 篩選 `payments` 明細，支援 `付款成功`、`退款處理中`、`退款申請中`、`已退款`、`已取消`，未帶時回傳全部付款明細。`payments` 支援 `paymentPage`、`paymentPageSize` 分頁，`pageSize` 最大 10 筆。

`data` 主要包含：

| 區塊 | 說明 |
| --- | --- |
| `event` | 活動圖片、名稱、活動狀態、活動日期、地點、攤位總數、已付款攤位數。 |
| `summary` | 收款總額、退款總額、已退款保證金、未退款保證金、實收總額。 |
| `statistics` | 付款、退款、保證金統計。 |
| `payments` | 付款明細分頁物件。 |

`payments` 分頁欄位：

| 欄位 | 說明 |
| --- | --- |
| `totalCount` | 符合條件的付款明細總筆數。 |
| `items` | 目前頁付款明細。 |
| `page` | 目前頁碼，從 1 開始。 |
| `pageSize` | 每頁筆數，最大 10。 |
| `totalItems` | 符合條件的付款明細總筆數。 |
| `totalPages` | 總頁數。 |
| `hasPrevious` | 是否有上一頁。 |
| `hasNext` | 是否有下一頁。 |

`payments.items` 每列欄位：

| 欄位 | 說明 |
| --- | --- |
| `paymentNo` | 付款編號。 |
| `brandName` | 品牌名稱。 |
| `paidAt` | 付款時間。 |
| `paymentAmount` | 付款金額。 |
| `refundAmount` | 已完成退款金額；只有 `accountingStatus = 已退款` 時才會顯示實際退款金額，退款申請中與退款處理中回 `0`。 |
| `depositStatus` | 保證金狀態。 |
| `accountingStatus` | 帳務狀態。 |

`payments` 不回傳攤位編號；帳務頁若需要攤位資訊，應由攤位管理相關 API 查詢。

```json
{
  "statusCode": 200,
  "message": "主辦方帳務詳情取得成功",
  "messageDetails": null,
  "data": {
    "event": {
      "eventId": 3,
      "coverImageUrl": "/uploads/events/t7-stall-03-cover.jpg",
      "eventTitle": "T7-STALL-03 已額滿市集",
      "publishStatus": "PUBLISHED",
      "publishStatusText": "報名中",
      "eventDate": "2026-07-29 - 2026-07-30",
      "locationName": "T7 Venue 3",
      "address": "New Taipei CityBanqiao DistrictT7 Test Road 3",
      "totalStallCount": 5,
      "paidStallCount": 5
    },
    "summary": {
      "grossRevenue": 7500,
      "refundAmount": 1500,
      "returnedDepositAmount": 0,
      "unreturnedDepositAmount": 1500,
      "netRevenue": 6000
    },
    "statistics": {
      "payment": {
        "totalStallCount": 5,
        "paidStallCount": 5,
        "pendingPaymentStallCount": 0
      },
      "refund": {
        "refundCount": 3,
        "refundedCount": 1,
        "refundingCount": 2
      },
      "deposit": {
        "returnedDepositCount": 0,
        "returnedDepositAmount": 0,
        "unreturnedDepositCount": 5,
        "unreturnedDepositAmount": 1500
      }
    },
    "payments": {
      "totalCount": 2,
      "items": [
        {
          "paymentNo": "PAY-T7-FULL-04",
          "brandName": "T7 滿額攤商 4",
          "paidAt": "2026-07-07 10:40",
          "paymentAmount": 1500,
          "refundAmount": 1500,
          "depositStatus": "未退還",
          "accountingStatus": "已退款"
        },
        {
          "paymentNo": "PAY-T7-FULL-03",
          "brandName": "T7 滿額攤商 3",
          "paidAt": "2026-07-07 11:00",
          "paymentAmount": 1500,
          "refundAmount": 0,
          "depositStatus": "未退還",
          "accountingStatus": "退款處理中"
        }
      ],
      "page": 1,
      "pageSize": 10,
      "totalItems": 2,
      "totalPages": 1,
      "hasPrevious": false,
      "hasNext": false
    }
  }
}
```

## Organizer 設備詳情

`GET /api/organizer/equipment/{eventId}`

需要 `Authorization` header。`equipmentRentalManagement`、`extraPowerManagement`、`vehicleManagement` 三個管理列表支援獨立分頁：

| 清單 | Page 參數 | PageSize 參數 |
| --- | --- | --- |
| `equipmentRentalManagement` | `equipmentRentalPage` | `equipmentRentalPageSize` |
| `extraPowerManagement` | `extraPowerPage` | `extraPowerPageSize` |
| `vehicleManagement` | `vehiclePage` | `vehiclePageSize` |

三個管理列表皆回傳 `totalCount`、`items`、`page`、`pageSize`、`totalItems`、`totalPages`、`hasPrevious`、`hasNext`；`pageSize` 最大 10 筆。Excel 匯出 API 不套用這些分頁參數，仍輸出完整資料。

## 錯誤訊息

錯誤訊息會直接回傳中文。
Service 或 Filter 即使傳入英文 key，也會透過 `ApiResponse.fail(...)` 轉成中文。

### Email 重複註冊

```json
{
  "statusCode": 400,
  "message": "此 Email 已被註冊",
  "messageDetails": null,
  "data": null
}
```

### 登入失敗

```json
{
  "statusCode": 400,
  "message": "Email 或密碼錯誤",
  "messageDetails": null,
  "data": null
}
```

### DTO 驗證失敗

```json
{
  "statusCode": 400,
  "message": "資料驗證失敗：電子信箱：電子信箱格式不正確; 密碼：密碼為必填",
  "messageDetails": null,
  "data": null
}
```

### JWT 未提供

```json
{
  "statusCode": 401,
  "message": "請提供授權 token",
  "messageDetails": null,
  "data": null
}
```

### JWT 無效或過期

```json
{
  "statusCode": 401,
  "message": "Token 無效或已過期",
  "messageDetails": null,
  "data": null
}
```

### Session 過期

```json
{
  "statusCode": 401,
  "message": "登入狀態已過期，請重新登入",
  "messageDetails": null,
  "data": null
}
```

## 實作注意事項

- Controller 應直接回傳 `ApiResponse<T>`，不要再回傳 `Map<String, Object>` 作為正式 response。
- 成功資料請使用 `dto/response` 內的 Response DTO。
- Request body 請使用 `dto/request` 內的 Request DTO。
- 錯誤請使用 `ApiResponse.fail(...)`，讓錯誤訊息可統一轉成中文。
- JWT Filter 失敗也會透過 `ApiResponse.fail(statusCode, message)` 回傳中文錯誤。
- `messageDetails` 目前通常為 `null`；舊版由 `GlobalResponseAdvice` 自動補 `Executed API: ...` 的設計已不是主要資料傳遞方式。

## 2026-07-07 更新：Organizer 申請詳情目前版

`GET /api/organizer/applications/{id}`

此 API 目前回傳重點：

- `message` 會由 `ApiResponse.success(...)` 統一轉為中文。
- `status` 固定回傳完整狀態流清單，未到達的節點以 `value: null`、`createdAt: null` 表示。
- `event.eventStatus` 統一回傳 `活動預告`、`即將開始`、`進行中`、`已結束`。
- `applicationdetail.registrationPeriods` 是單一字串，例如 `2026-06-28 11:00-19:00 - 2026-06-29 11:00-19:00`。
- `stall` 依報名日期回傳陣列，未選位日期也會回傳 `selectionStatus: 未選擇`。
- `fee` 只保留付款狀態、付款方式、付款編號與付款金額。
- `feedetail` 回傳報名費、設備租借費、額外電費、保證金與總計；`content` 會帶日期、設備名稱數量或電力規格。
- `equipmentRentals` 分成 `freeEquipments`、`freeBasicPower`、`rentalEquipments`、`extraPower` 四區。

```json
{
  "statusCode": 200,
  "message": "主辦方申請詳情取得成功",
  "messageDetails": null,
  "data": {
    "application": {
      "applicationId": 101,
      "applicationNo": "T2-ORDER-0",
      "applicationStatus": "報名完成"
    },
    "event": {
      "eventTitle": "T5-APPLICATION-STATUS",
      "eventStatus": "已結束",
      "eventTime": "2026-06-28 - 2026-06-29",
      "address": "Taipei CityXinyi DistrictT5 Application Status Address 1"
    },
    "vendor": {
      "vendorOwnerName": "test2 vendor 0 owner",
      "vendorPhone": "0933000000",
      "vendorEmail": "test2vendor0@example.test",
      "address": "Taipei CityXinyi DistrictTest Vendor Road 0"
    },
    "brand": {
      "brandName": "test2 vendor 0 brand",
      "categoryName": "Food",
      "brandDescription": "test2 vendor 0 brand description"
    },
    "applicationdetail": {
      "registrationPeriods": "2026-06-28 11:00-19:00 - 2026-06-29 11:00-19:00",
      "stallSize": "3x2.5",
      "stallZone": "A",
      "stallCategory": "Food",
      "vehicleNo": "T5-CAR-06",
      "applicantNote": "報名完成 test application.",
      "reviewNote": null,
      "reviewNoteDetail": null
    },
    "stall": [
      {
        "applyDate": "2026-06-28",
        "stallNo": "A06",
        "zoneName": "A",
        "selectionStatus": "已選擇"
      },
      {
        "applyDate": "2026-06-29",
        "stallNo": "A06",
        "zoneName": "A",
        "selectionStatus": "已選擇"
      }
    ],
    "fee": {
      "paymentStatus": "付款成功",
      "paymentMethod": "TEST",
      "paymentNo": "PAY-T5-APP-06",
      "paymentAmount": 5400
    },
    "feedetail": [
      { "item": "報名費", "content": "2天 (2026-06-28、2026-06-29)", "amount": 2400 },
      { "item": "設備租借費", "content": "帳篷租借*2、冷藏櫃租借*1", "amount": 2100 },
      { "item": "額外電費", "content": "110V / 500W*2", "amount": 600 },
      { "item": "保證金", "content": "保證金", "amount": 300 },
      { "item": "總計", "content": null, "amount": 5400 }
    ],
    "equipmentRentals": {
      "freeEquipments": [
        {
          "equipmentName": "攤位椅",
          "specification": "一般折疊椅，每攤基本提供。",
          "quantity": 2,
          "unit": "個",
          "subtotal": 0
        }
      ],
      "freeBasicPower": [
        {
          "powerSpecification": "110V / 300W",
          "wattage": 300,
          "unitPrice": 0,
          "subtotal": 0
        }
      ],
      "rentalEquipments": [
        {
          "equipmentName": "帳篷租借",
          "specification": "3x3m 防水帳篷，含基本搭設。",
          "quantity": 2,
          "unit": "天",
          "subtotal": 1600,
          "subtotalContent": "共2天",
          "total": 1600
        }
      ],
      "extraPower": [
        {
          "powerSpecification": "110V / 500W",
          "wattage": 500,
          "unitPrice": 150,
          "unit": "天",
          "subtotal": 600,
          "subtotalContent": "共2天",
          "total": 600
        }
      ]
    },
    "status": [
      { "key": "APPLIED", "label": "報名日期", "value": "已報名", "createdAt": "2026-07-01 09:00" },
      { "key": "REVIEW", "label": "審核時間", "value": "審核通過", "createdAt": "2026-07-02 10:00" },
      { "key": "CANCELLED", "label": "取消時間", "value": null, "createdAt": null },
      { "key": "PAYMENT", "label": "付款時間", "value": "付款成功", "createdAt": "2026-07-03 11:00" },
      { "key": "REFUND_REQUESTED", "label": "退款申請時間", "value": null, "createdAt": null },
      { "key": "REFUND_REVIEW", "label": "退款審核時間", "value": null, "createdAt": null },
      { "key": "REFUNDED", "label": "已退款時間", "value": null, "createdAt": null },
      { "key": "STALL_SELECTED", "label": "選位時間", "value": "已選位", "createdAt": "2026-07-04 12:00" },
      { "key": "DEPOSIT_RETURNED", "label": "保證金退還時間", "value": null, "createdAt": null }
    ]
  }
}
```
