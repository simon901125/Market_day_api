package com.example.demo.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.entity.Notification;

public interface NotificationRepo extends JpaRepository<Notification, Long> {
}
