package com.pokesim.service;

import com.pokesim.api.dto.BaseStatsDto;
import com.pokesim.api.dto.MoveResponse;
import com.pokesim.api.dto.PageResponse;
import com.pokesim.api.dto.SpeciesDetail;
import com.pokesim.api.dto.SpeciesSummary;
import com.pokesim.api.error.BadRequestException;
import com.pokesim.api.error.NotFoundException;
import com.pokesim.engine.PokemonType;
import com.pokesim.engine.Ruleset;
import com.pokesim.persistence.MoveEntity;
import com.pokesim.persistence.MoveRepository;
import com.pokesim.persistence.SpeciesEntity;
import com.pokesim.persistence.SpeciesRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only queries over the reference data (the "dex"). */
@Service
@Transactional(readOnly = true)
public class DexService {
    static final String VERSION_GROUP = "firered-leafgreen";

    private final SpeciesRepository species;
    private final MoveRepository moves;
    private final Ruleset rules;

    public DexService(SpeciesRepository species, MoveRepository moves, Ruleset rules) {
        this.species = species;
        this.moves = moves;
        this.rules = rules;
    }

    public PageResponse<SpeciesSummary> listSpecies(String type, String name, int page, int size) {
        Specification<SpeciesEntity> spec = (root, query, cb) -> cb.conjunction();
        PokemonType parsed = parseType(type);
        if (parsed != null) {
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.equal(root.get("primaryType"), parsed), cb.equal(root.get("secondaryType"), parsed)));
        }
        if (name != null && !name.isBlank()) spec = spec.and(nameContains(name));
        // Sort is fixed on purpose: accepting arbitrary sort fields from the client is an injection-style risk.
        return PageResponse.of(species.findAll(spec, PageRequest.of(page, size, Sort.by("dexNumber"))).map(this::summary));
    }

    public SpeciesDetail getSpecies(int dex) {
        return detail(find(dex));
    }

    public List<MoveResponse> learnset(int dex) {
        SpeciesEntity s = find(dex);
        return moves.findLearnable(s.getId(), VERSION_GROUP).stream().map(this::moveResponse).toList();
    }

    public PageResponse<MoveResponse> listMoves(String type, String name, int page, int size) {
        Specification<MoveEntity> spec = (root, query, cb) -> cb.conjunction();
        PokemonType parsed = parseType(type);
        if (parsed != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), parsed));
        if (name != null && !name.isBlank()) spec = spec.and(nameContains(name));
        return PageResponse.of(moves.findAll(spec, PageRequest.of(page, size, Sort.by("name"))).map(this::moveResponse));
    }

    public MoveResponse getMove(int id) {
        return moves.findById(id).map(this::moveResponse)
                .orElseThrow(() -> new NotFoundException("No move with id " + id));
    }

    // ------------------------------------------------------------------ helpers

    private SpeciesEntity find(int dex) {
        return species.findByDexNumber(dex)
                .orElseThrow(() -> new NotFoundException("No species with dex number " + dex + " in this game"));
    }

    /** Case-insensitive; rejects unknown values with a helpful message instead of a 500. */
    private PokemonType parseType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            PokemonType type = PokemonType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            if (type != PokemonType.TYPELESS) return type;
        } catch (IllegalArgumentException ignored) {
            // fall through to the error below
        }
        String valid = Arrays.stream(PokemonType.values()).filter(t -> t != PokemonType.TYPELESS)
                .map(t -> t.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", "));
        throw new BadRequestException("Unknown type '" + raw + "'. Valid types: " + valid);
    }

    /** Escapes LIKE wildcards so a search for "%" or "_" matches literally. */
    private static <T> Specification<T> nameContains(String name) {
        String pattern = "%" + name.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, '\\');
    }

    private List<String> typeNames(SpeciesEntity s) {
        return s.getSecondaryType() == null
                ? List.of(s.getPrimaryType().name())
                : List.of(s.getPrimaryType().name(), s.getSecondaryType().name());
    }

    private SpeciesSummary summary(SpeciesEntity s) {
        return new SpeciesSummary(s.getDexNumber(), s.getName(), typeNames(s));
    }

    private SpeciesDetail detail(SpeciesEntity s) {
        return new SpeciesDetail(s.getDexNumber(), s.getName(), s.getGeneration(), typeNames(s),
                new BaseStatsDto(s.getBaseHp(), s.getBaseAttack(), s.getBaseDefense(),
                        s.getBaseSpAttack(), s.getBaseSpDefense(), s.getBaseSpeed()));
    }

    private MoveResponse moveResponse(MoveEntity m) {
        // Physical/special comes from the ruleset (Gen 3: decided by type), not from the database.
        return new MoveResponse(m.getId(), m.getName(), m.getType().name(), rules.categoryOf(m.toEngine()).name(),
                m.getPower(), m.getAccuracy(), m.getPp(), m.getPriority(), m.getAilment().name(),
                m.getAilmentChance(), m.getRecoilDivisor());
    }
}
