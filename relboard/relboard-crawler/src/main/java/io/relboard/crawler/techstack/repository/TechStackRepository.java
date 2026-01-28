package io.relboard.crawler.techstack.repository;

import io.relboard.crawler.techstack.domain.TechStack;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechStackRepository extends JpaRepository<TechStack, Long> {

  Optional<TechStack> findByName(String name);
}
