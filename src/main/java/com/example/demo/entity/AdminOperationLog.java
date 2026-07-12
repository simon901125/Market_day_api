package com.example.demo.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.example.demo.enums.type.AdminOperationType;
import com.example.demo.enums.type.AdminTargetType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "admin_operation_logs")
public class AdminOperationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "admin_user_id", nullable = false, foreignKey = @ForeignKey(name = "FK_admin_operation_logs_admin_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 30)
    private AdminOperationType operationType;

    @Column(name = "target_type", nullable = false, length = 30)
    private AdminTargetType targetType;

    @Column(name = "target_id")
    private Long targetId;
    
    @Column(name = "target_label", nullable = false, length = 200)
    private String targetLabel;

    @Column(name = "action_content", nullable = false, length = 300)
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
