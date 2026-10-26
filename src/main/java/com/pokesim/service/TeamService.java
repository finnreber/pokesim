package com.pokesim.service;

import com.pokesim.api.dto.MemberRequest;
import com.pokesim.api.dto.MemberResponse;
import com.pokesim.api.dto.MoveRef;
import com.pokesim.api.dto.PageResponse;
import com.pokesim.api.dto.TeamRequest;
import com.pokesim.api.dto.TeamResponse;
import com.pokesim.api.error.ConflictException;
import com.pokesim.api.error.InvalidTeamException;
import com.pokesim.api.error.NotFoundException;
import com.pokesim.persistence.MoveEntity;
import com.pokesim.persistence.MoveRepository;
import com.pokesim.persistence.SpeciesEntity;
import com.pokesim.persistence.SpeciesRepository;
import com.pokesim.persistence.TeamEntity;
import com.pokesim.persistence.TeamMemberEntity;
import com.pokesim.persistence.TeamRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamService {
    private final TeamRepository teams;
    private final SpeciesRepository species;
    private final MoveRepository moves;

    public TeamService(TeamRepository teams, SpeciesRepository species, MoveRepository moves) {
        this.teams = teams;
        this.species = species;
        this.moves = moves;
    }

    /** A member that has passed every legality check. */
    private record ResolvedMember(SpeciesEntity species, int level, List<Integer> moveIds) { }

    @Transactional
    public TeamResponse create(UUID ownerId, TeamRequest request) {
        if (teams.existsByOwnerIdAndName(ownerId, request.name())) throw duplicateName(request.name());
        List<ResolvedMember> resolved = validate(request);
        TeamEntity team = new TeamEntity(ownerId, request.name());
        addMembers(team, resolved);
        teams.save(team);
        return toResponse(team, moveNames(List.of(team)));
    }

    @Transactional(readOnly = true)
    public PageResponse<TeamResponse> list(UUID ownerId, int page, int size) {
        Page<TeamEntity> result = teams.findByOwnerId(ownerId, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        Map<Integer, String> names = moveNames(result.getContent());
        return PageResponse.of(result.map(team -> toResponse(team, names)));
    }

    @Transactional(readOnly = true)
    public TeamResponse get(UUID ownerId, UUID teamId) {
        TeamEntity team = owned(ownerId, teamId);
        return toResponse(team, moveNames(List.of(team)));
    }

    @Transactional
    public TeamResponse update(UUID ownerId, UUID teamId, TeamRequest request) {
        TeamEntity team = owned(ownerId, teamId);
        if (teams.existsByOwnerIdAndNameAndIdNot(ownerId, request.name(), teamId)) throw duplicateName(request.name());
        List<ResolvedMember> resolved = validate(request);

        team.rename(request.name());
        team.getMembers().clear();
        // Flush now so the old rows are DELETEd before the new ones are INSERTed. Hibernate's default order is
        // inserts-first, which would violate UNIQUE (team_id, party_position).
        teams.flush();
        addMembers(team, resolved);
        team.touch();
        return toResponse(team, moveNames(List.of(team)));
    }

    @Transactional
    public void delete(UUID ownerId, UUID teamId) {
        teams.delete(owned(ownerId, teamId));
    }

    // ------------------------------------------------------------------ legality rules

    /** Checks every rule and reports ALL problems at once, so the client can fix them in one round trip. */
    private List<ResolvedMember> validate(TeamRequest request) {
        List<String> errors = new ArrayList<>();
        List<ResolvedMember> resolved = new ArrayList<>();
        for (int i = 0; i < request.members().size(); i++) {
            MemberRequest member = request.members().get(i);
            String where = "members[" + i + "]";
            Optional<SpeciesEntity> found = species.findByDexNumber(member.dexNumber());
            if (found.isEmpty()) {
                errors.add(where + ": species " + member.dexNumber() + " is not in this game");
                continue;
            }
            SpeciesEntity s = found.get();
            Set<Integer> learnable = moves.findLearnable(s.getId(), DexService.VERSION_GROUP).stream()
                    .map(MoveEntity::getId).collect(Collectors.toSet());
            Set<Integer> seen = new HashSet<>();
            for (Integer moveId : member.moveIds()) {
                if (!seen.add(moveId)) {
                    errors.add(where + ": move " + moveId + " is listed twice");
                } else if (!learnable.contains(moveId)) {
                    errors.add(where + ": " + s.getName() + " cannot learn move " + moveId + " in this game");
                }
            }
            resolved.add(new ResolvedMember(s, member.level(), member.moveIds()));
        }
        if (!errors.isEmpty()) throw new InvalidTeamException(errors);
        return resolved;
    }

    private void addMembers(TeamEntity team, List<ResolvedMember> resolved) {
        for (int i = 0; i < resolved.size(); i++) {
            ResolvedMember m = resolved.get(i);
            team.getMembers().add(new TeamMemberEntity(team, i, m.species(), m.level(), m.moveIds()));
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Missing and not-yours look identical (404), so nobody can probe which team ids exist. */
    private TeamEntity owned(UUID ownerId, UUID teamId) {
        return teams.findByIdAndOwnerId(teamId, ownerId)
                .orElseThrow(() -> new NotFoundException("No team with id " + teamId));
    }

    private ConflictException duplicateName(String name) {
        return new ConflictException("You already have a team named '" + name + "'");
    }

    private Map<Integer, String> moveNames(Collection<TeamEntity> teamList) {
        Set<Integer> ids = teamList.stream().flatMap(t -> t.getMembers().stream())
                .flatMap(m -> m.getMoveIds().stream()).collect(Collectors.toSet());
        return moves.findAllById(ids).stream().collect(Collectors.toMap(MoveEntity::getId, MoveEntity::getName));
    }

    private TeamResponse toResponse(TeamEntity team, Map<Integer, String> names) {
        List<MemberResponse> members = team.getMembers().stream()
                .map(m -> new MemberResponse(m.getSpecies().getDexNumber(), m.getSpecies().getName(), m.getLevel(),
                        m.getMoveIds().stream().map(id -> new MoveRef(id, names.get(id))).toList()))
                .toList();
        return new TeamResponse(team.getId(), team.getName(), members, team.getCreatedAt(), team.getUpdatedAt());
    }
}
