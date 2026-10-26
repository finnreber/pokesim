package com.pokesim.api.error;

import java.util.List;

/** The JSON was well-formed but the team breaks the game's rules (e.g. a move the species cannot learn). */
public class InvalidTeamException extends RuntimeException {
    private final List<String> errors;

    public InvalidTeamException(List<String> errors) {
        super("Team is not legal: " + errors.size() + " problem(s)");
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() { return errors; }
}
