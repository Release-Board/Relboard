package io.relboard.crawler.techstack.repository;

import io.relboard.crawler.techstack.domain.TechStack;
import io.relboard.crawler.techstack.domain.TechStackSource;
import io.relboard.crawler.techstack.domain.TechStackSourceType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechStackSourceRepository extends JpaRepository<TechStackSource, Long> {

  Optional<TechStackSource> findByTechStackAndType(TechStack techStack, TechStackSourceType type);
}
