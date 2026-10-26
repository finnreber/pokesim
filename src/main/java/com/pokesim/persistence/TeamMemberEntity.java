package com.pokesim.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "team_members")
public class TeamMemberEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private TeamEntity team;

    @Column(name = "party_position", nullable = false)
    private int position;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "species_id", nullable = false)
    private SpeciesEntity species;

    @Column(nullable = false)
    private int level;

    // Move ids in slot order. @OrderColumn stores the list index in the "slot" column.
    @ElementCollection
    @CollectionTable(name = "team_member_moves", joinColumns = @JoinColumn(name = "member_id"))
    @OrderColumn(name = "slot")
    @Column(name = "move_id", nullable = false)
    private List<Integer> moveIds = new ArrayList<>();

    protected TeamMemberEntity() { }

    public TeamMemberEntity(TeamEntity team, int position, SpeciesEntity species, int level, List<Integer> moveIds) {
        this.team = team;
        this.position = position;
        this.species = species;
        this.level = level;
        this.moveIds = new ArrayList<>(moveIds);
    }

    public UUID getId() { return id; }
    public TeamEntity getTeam() { return team; }
    public int getPosition() { return position; }
    public SpeciesEntity getSpecies() { return species; }
    public int getLevel() { return level; }
    public List<Integer> getMoveIds() { return moveIds; }
}
