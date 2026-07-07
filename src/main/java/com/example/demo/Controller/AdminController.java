package com.example.demo.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.Service.AdminService;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.admin.AdminDashboardDto;

import io.swagger.v3.oas.annotations.tags.Tag;


@RestController
@RequestMapping("/api/admin")
@Tag(name = "管理員API", description = "提供與管理員活動審核、使用者帳號停復用相關功能")
public class AdminController {
    @Autowired
    AdminService service;

    @GetMapping("/dashboard/overview")
    public ApiResponse<AdminDashboardDto> showDashboardOverview(){
        AdminDashboardDto response = service.setDashboardResponse();
        return ApiResponse.success("ok", response);
    }

}
