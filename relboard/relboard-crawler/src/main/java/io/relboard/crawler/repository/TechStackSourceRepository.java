package io.relboard.crawler.repository;

import io.relboard.crawler.domain.TechStackSource;
import io.relboard.crawler.domain.TechStackSourceType;
import io.relboard.crawler.domain.TechStack;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechStackSourceRepository extends JpaRepository<TechStackSource, Long> {

  Optional<TechStackSource> findByTechStackAndType(TechStack techStack, TechStackSourceType type);
}
