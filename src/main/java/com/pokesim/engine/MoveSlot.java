package com.pokesim.engine;

/** A move a specific Pokemon knows, plus its remaining PP (mutable battle state). */
public final class MoveSlot {
    private final Move move;
    private int pp;

    public MoveSlot(Move move) {
        this.move = move;
        this.pp = move.pp();
    }

    public Move move() { return move; }
    public int pp() { return pp; }
    public void usePp() { if (pp > 0) pp--; }
}
