package com.example.demo.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 報名參與日期的Entity。<br>
 * 包含參與日期<br>
 * <b>FK</b>: 攤位報名{@link EventApplication#applicationDates}、當日已選定的攤位{@link EventStall#applicationDates}
 */
@Entity
@Data
@Table(name = "application_dates", uniqueConstraints = @UniqueConstraint(name = "UQ_application_dates_application_date", columnNames = {
        "application_id", "apply_date" }))
public class ApplicationDate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**所屬的攤位報名 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false, foreignKey = @ForeignKey(name = "FK_application_dates_event_applications"))
    private EventApplication application;

    /**參與日期 */
    @Column(name = "apply_date", nullable = false)
    private LocalDate applyDate;

    /**當日已選定的攤位 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_stall_id", foreignKey = @ForeignKey(name = "FK_application_dates_event_stalls"))
    private EventStall selectedStall;

}
