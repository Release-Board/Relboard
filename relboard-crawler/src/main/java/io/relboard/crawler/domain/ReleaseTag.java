package io.relboard.crawler.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "release_tag")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReleaseTag extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "release_id")
    private ReleaseRecord releaseRecord;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag_type", nullable = false)
    private ReleaseTagType tagType;

    @Builder
    private ReleaseTag(Long id, ReleaseRecord releaseRecord, ReleaseTagType tagType) {
        this.id = id;
        this.releaseRecord = releaseRecord;
        this.tagType = tagType;
    }
}
