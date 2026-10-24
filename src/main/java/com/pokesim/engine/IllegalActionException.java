package com.pokesim.engine;

/** Thrown when a player submits an action that is not legal in the current battle state. */
public class IllegalActionException extends RuntimeException {
    public IllegalActionException(String message) { super(message); }
}
