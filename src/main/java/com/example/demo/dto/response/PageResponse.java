package com.example.demo.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "分頁查詢結果")
public class PageResponse<T> {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 1000;

    @Schema(description = "目前頁資料")
    private List<T> items;

    @Schema(description = "目前頁碼，從 1 開始", example = "1")
    private int page;

    @Schema(description = "每頁筆數", example = "10")
    private int pageSize;

    @Schema(description = "符合條件的總筆數", example = "135")
    private long totalItems;

    @Schema(description = "總頁數", example = "7")
    private int totalPages;

    @Schema(description = "是否有上一頁", example = "false")
    private boolean hasPrevious;

    @Schema(description = "是否有下一頁", example = "true")
    private boolean hasNext;

    public PageResponse(List<T> items, int page, int pageSize, long totalItems) {
        this.items = items == null ? List.of() : items;
        this.page = normalizePage(page);
        this.pageSize = normalizePageSize(pageSize);
        this.totalItems = Math.max(totalItems, 0);
        this.totalPages = calculateTotalPages(this.totalItems, this.pageSize);
        this.hasPrevious = this.page > 1 && this.totalPages > 0;
        this.hasNext = this.totalPages > 0 && this.page < this.totalPages;
    }

    public static <T> PageResponse<T> from(List<T> allItems, Integer page, Integer pageSize) {
        List<T> source = allItems == null ? List.of() : allItems;
        int normalizedPage = normalizePage(page);
        int normalizedPageSize = normalizePageSize(pageSize);
        int fromIndex = Math.min((normalizedPage - 1) * normalizedPageSize, source.size());
        int toIndex = Math.min(fromIndex + normalizedPageSize, source.size());
        return new PageResponse<>(
                source.subList(fromIndex, toIndex),
                normalizedPage,
                normalizedPageSize,
                source.size());
    }

    public static int normalizePage(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    public static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return Math.min(DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private static int calculateTotalPages(long totalItems, int pageSize) {
        if (totalItems <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalItems / pageSize);
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public long getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(long totalItems) {
        this.totalItems = totalItems;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public boolean isHasPrevious() {
        return hasPrevious;
    }

    public void setHasPrevious(boolean hasPrevious) {
        this.hasPrevious = hasPrevious;
    }

    public boolean isHasNext() {
        return hasNext;
    }

    public void setHasNext(boolean hasNext) {
        this.hasNext = hasNext;
    }
}
