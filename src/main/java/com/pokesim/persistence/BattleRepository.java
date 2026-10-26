package com.pokesim.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BattleRepository extends JpaRepository<BattleEntity, UUID> {
    Page<BattleEntity> findByPhaseAndPlayer1IdNot(BattlePhase phase, UUID player1Id, Pageable pageable);

    @Query("select b from BattleEntity b where b.player1Id = :userId or b.player2Id = :userId")
    Page<BattleEntity> findMine(@Param("userId") UUID userId, Pageable pageable);
}
