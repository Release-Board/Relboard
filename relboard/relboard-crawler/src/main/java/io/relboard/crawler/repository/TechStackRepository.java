package io.relboard.crawler.repository;

import io.relboard.crawler.domain.TechStack;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechStackRepository extends JpaRepository<TechStack, Long> {

  Optional<TechStack> findByName(String name);
}
