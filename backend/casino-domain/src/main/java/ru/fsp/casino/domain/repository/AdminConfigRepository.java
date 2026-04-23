package ru.fsp.casino.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.fsp.casino.domain.model.AdminConfig;

public interface AdminConfigRepository extends JpaRepository<AdminConfig, Integer> {
}
