package io.relboard.crawler.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "tech_stack")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TechStack extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "latest_version")
    private String latestVersion;

    @Column
    private String category;

    @Builder
    private TechStack(Long id, String name, String latestVersion, String category) {
        this.id = id;
        this.name = name;
        this.latestVersion = latestVersion;
        this.category = category;
    }

    public void updateLatestVersion(String latestVersion) {
        this.latestVersion = latestVersion;
    }
}
