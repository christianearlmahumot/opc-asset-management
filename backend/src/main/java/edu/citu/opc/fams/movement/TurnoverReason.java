package edu.citu.opc.fams.movement;

/** UC-302: the standard list that replaces free-text remarks such as "DEFECTIVE", "NO LONGER USED". */
public enum TurnoverReason {
    DEFECTIVE("Defective"),
    DAMAGED("Damaged"),
    NO_LONGER_USED("No longer used"),
    FOR_UPGRADE("For upgrade"),
    OTHER("Other");

    private final String label;

    TurnoverReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
