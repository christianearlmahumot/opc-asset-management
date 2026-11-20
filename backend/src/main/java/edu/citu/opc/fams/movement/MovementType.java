package edu.citu.opc.fams.movement;

public enum MovementType {
    ENCODE("Encoded"),
    ISSUE("Issued"),
    TRANSFER("Transferred"),
    CONDITION("Condition"),
    TURNOVER("Turned over"),
    RESIGNATION("Resignation turnover"),
    DISPOSAL("Disposed");

    private final String label;

    MovementType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
