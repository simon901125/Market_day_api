# Push 前測試流程

本文件說明 Market Day API 在執行 `git push` 前應完成的本機測試。

目前測試分成兩個階段：

1. 單元、Controller、Filter 與 Spring Boot 啟動冒煙測試。
2. SQL Server schema、Repository CRUD 與 JDBC 查詢整合測試。

目前共有 168 筆測試：一般測試 148 筆、SQL Server 整合測試 20 筆。後續新增 API 與測試時，筆數可以繼續增加。

## 一、第一次執行前的準備

### 1. 確認 SQL Server 已啟動

整合測試使用獨立資料庫：

```text
MarketDayDB_Test
```

測試程式只允許重建名稱為 `MarketDayDB_Test` 的資料庫，不會重建開發資料庫 `MarketDayDB`。

### 2. 設定本機資料庫帳密

在 Git 已忽略的 `demo/run-local.cmd` 內設定：

```cmd
set DB_USERNAME=你的 SQL Server 帳號
set DB_PASSWORD=你的 SQL Server 密碼
```

`src/test/run-test.cmd` 會讀取這兩個值，轉成整合測試使用的 `TEST_DB_USERNAME` 與 `TEST_DB_PASSWORD`。不要將帳號密碼寫入測試原始碼或提交到 Git。

### 3. 確認測試 schema 已同步

SQL Server 整合測試使用：

```text
src/test/resources/sql/MarketDayDBIntegration.sql
```

若主要資料庫 SQL 有新增、刪除或重新命名資料表、欄位、constraint 或 index，必須同步更新這份測試 schema。

## 二、Push 前完整測試

在 `demo` 目錄執行：

```cmd
src\test\run-test.cmd
```

腳本會依序執行：

```text
[1/2] Unit / Controller / Filter / Smoke tests
[2/2] SQL Server integration tests
```

第一階段執行：

```cmd
mvnw.cmd clean test
```

第二階段會重建 `MarketDayDB_Test`，再執行所有名稱以 `IT` 結尾的測試：

```cmd
mvnw.cmd "-Dtest=*IT" test
```

只有最後看到以下訊息，才代表完整測試通過：

```text
[SUCCESS] All unit, controller, smoke, and SQL Server integration tests passed.
```

兩個 Maven 階段也都必須顯示：

```text
BUILD SUCCESS
```

## 三、只執行特定測試

### 只跑 SQL Server 整合測試

```cmd
src\test\run-test.cmd integration
```

適合修改 Entity、Repository、SQL 查詢或資料庫 schema 後快速驗證。

### 只跑一般測試

```cmd
mvnw.cmd clean test
```

這會執行單元、Controller、Filter 與啟動冒煙測試，但不包含 `*IT` SQL Server 整合測試，因此不能取代 push 前的完整測試。

### 只跑一個測試類別

PowerShell：

```powershell
.\mvnw.cmd "-Dtest=UserServiceTest" test
```

多個測試類別：

```powershell
.\mvnw.cmd "-Dtest=UserServiceTest,UserControllerTest" test
```

## 四、測試失敗時怎麼判斷

### `COMPILATION ERROR`

代表正式程式或測試程式無法編譯，常見原因包括：

- 方法參數或回傳型別已變更，但測試尚未同步。
- DTO、enum 或 class 已重新命名。
- import 錯誤或刪除了仍被使用的程式。

### `Failures`

測試成功執行，但實際結果與既有預期不同。應先判斷：

- 此次變更是否意外破壞既有行為。
- 若是刻意修改 API 規格，正式程式與對應測試是否都已正確更新。

不要只為了讓測試通過而移除 assertion。

### `Errors`

測試執行期間發生例外，常見原因包括：

- SQL Server 未啟動或帳密錯誤。
- Entity 與資料庫 schema 不一致。
- Repository SQL 使用不存在的欄位。
- 測試資料不符合 foreign key、check constraint 或 unique constraint。

### 日誌有 WARN，但最後是 `BUILD SUCCESS`

WARN 不一定代表測試失敗；是否通過以 `Failures: 0`、`Errors: 0` 與最後的 `BUILD SUCCESS` 為準。但 deprecated API 等警告仍應安排後續處理。

## 五、新增或修改 API 時的測試原則

完成 API 的同時就更新測試，不要等所有 API 完成後再一次補齊。

- Controller 改動：測試參數轉交、HTTP/API 回傳與錯誤處理。
- Service 改動：測試成功流程、驗證失敗、權限與重要分支。
- Repository 或 SQL 改動：更新 SQL Server 整合測試，確認查詢可在目前 schema 執行。
- Entity 或 schema 改動：同步 `MarketDayDBIntegration.sql`，執行 schema validation。
- Filter、JWT 或共用回應改動：更新對應單元測試及啟動冒煙測試。
- 修正 bug：先新增能重現問題的測試，再修正程式，避免問題再次發生。

不需要替純 getter/setter 或沒有邏輯的 DTO 重複撰寫低價值測試；應優先保護 API 合約、商業規則、權限、狀態轉換與資料庫查詢。

## 六、實際 Push 前檢查清單

- [ ] 新增或修改的功能已有對應測試。
- [ ] SQL schema 有異動時，整合測試 schema 已同步。
- [ ] 已執行 `src\test\run-test.cmd`。
- [ ] 一般測試與 SQL Server 整合測試皆為 `BUILD SUCCESS`。
- [ ] 測試結果為 `Failures: 0`、`Errors: 0`。
- [ ] `run-local.cmd`、`run-test.cmd`、密碼、圖片與本機產生檔案未被加入 Git。
- [ ] 使用 `git status` 確認實際準備提交的檔案。

以上全部完成後，再執行 commit 與 push。
