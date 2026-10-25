package com.pokesim.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SpeciesRepository extends JpaRepository<SpeciesEntity, Integer>, JpaSpecificationExecutor<SpeciesEntity> {
    Optional<SpeciesEntity> findByDexNumber(int dexNumber);
}
