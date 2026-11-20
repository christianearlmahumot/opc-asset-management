package edu.citu.opc.fams.asset;

/** UC-302 conditions, labelled as on the OPC forms. */
public enum AssetCondition {
    GOOD("Good Condition"),
    FUNCTIONAL("Functional"),
    DEFECTIVE("Defective"),
    DAMAGED("Damaged");

    private final String label;

    AssetCondition(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
