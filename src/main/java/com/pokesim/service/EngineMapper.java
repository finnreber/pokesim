package com.pokesim.service;

import com.pokesim.engine.PokemonType;
import com.pokesim.engine.Species;
import com.pokesim.engine.StatBlock;
import com.pokesim.persistence.SpeciesEntity;
import java.util.List;

/** Database rows -> pure engine objects. The only place that knows both shapes. */
final class EngineMapper {
    private EngineMapper() { }

    static Species species(SpeciesEntity s) {
        List<PokemonType> types = s.getSecondaryType() == null
                ? List.of(s.getPrimaryType())
                : List.of(s.getPrimaryType(), s.getSecondaryType());
        return new Species(s.getDexNumber(), s.getName(), types, new StatBlock(s.getBaseHp(), s.getBaseAttack(),
                s.getBaseDefense(), s.getBaseSpAttack(), s.getBaseSpDefense(), s.getBaseSpeed()));
    }
}
