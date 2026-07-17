package com.example.demo.Repository.projection.admin;

/**
 * 管理員:操作者身份查詢欄位
 *
 * @param id 管理員帳號id
 * @param adminName 管理員名稱(adminProfile.name)
 *
 * @see com.example.demo.Repository.UserRepo#findAdminLookupByEmailAndRole(String, com.example.demo.enums.type.Role)
 */
public record AdminLookupProjection(
    Long id,
    String adminName) {}
