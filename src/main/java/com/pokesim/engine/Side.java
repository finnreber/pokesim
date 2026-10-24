package com.pokesim.engine;

public enum Side {
    P1, P2;

    public Side opponent() { return this == P1 ? P2 : P1; }
}
