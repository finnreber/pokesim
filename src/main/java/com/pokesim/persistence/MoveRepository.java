package com.pokesim.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MoveRepository extends JpaRepository<MoveEntity, Integer>, JpaSpecificationExecutor<MoveEntity> {

    /** The join table has no entity of its own, so a native query is simpler than modelling it. */
    @Query(value = """
            SELECT m.* FROM moves m
            JOIN learnable_move l ON l.move_id = m.id
            WHERE l.species_id = :speciesId AND l.version_group = :versionGroup
            ORDER BY m.name
            """, nativeQuery = true)
    List<MoveEntity> findLearnable(@Param("speciesId") int speciesId, @Param("versionGroup") String versionGroup);
}
