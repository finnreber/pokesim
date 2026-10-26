package com.pokesim.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<TeamEntity, UUID> {
    Page<TeamEntity> findByOwnerId(UUID ownerId, Pageable pageable);

    /** Ownership is part of the query itself, so a team that is not yours simply "does not exist". */
    Optional<TeamEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByOwnerIdAndName(UUID ownerId, String name);

    boolean existsByOwnerIdAndNameAndIdNot(UUID ownerId, String name, UUID id);
}
