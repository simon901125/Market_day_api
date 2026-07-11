package com.example.demo.entity;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

/**
 * 活動分類的Entity。<br>
 * 包含:分類名稱、分類代碼、分類是否啟用
 * 
 * @see MarketEvent
 */
@Entity
@Data
@Table(name = "categories", uniqueConstraints = @UniqueConstraint(name = "UQ_categories_slug", columnNames = "slug"))
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**分類名稱 */
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    /**分類代碼 */
    @Column(name = "slug", length = 100, nullable = false)
    private String slug;

    /**分類是否啟用*/
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @OneToMany(mappedBy = "category")
    private List<MarketEvent> marketEvents;
}
