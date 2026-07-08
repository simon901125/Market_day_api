package com.example.demo.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.Repository.EventRepo;
import com.example.demo.Repository.UserRepo;
import com.example.demo.dto.response.admin.AdminDashboardDto;

@Service
public class AdminService {
    @Autowired
    EventRepo activityRepo;

    @Autowired
    UserRepo userRepo;

    public AdminDashboardDto setDashboardResponse(){
        
        //TODO:要再確認狀態的對應
        return null;
    }
}
