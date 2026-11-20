package edu.citu.opc.fams.asset;

public enum AssetStatus {
    /** Encoded by a NAS Assistant, waiting for Property Staff to confirm (SRS 2.2). Not yet in the register. */
    DRAFT("Draft"),
    /** In OPC storage, available to issue. */
    IN_STORAGE("In OPC storage"),
    /** Assigned to a department and accountable person. */
    ISSUED("Issued"),
    /** Turned over to OPC storage as defective, damaged, no longer used… (UC-302). */
    TURNED_OVER("Turned over"),
    DISPOSED("Disposed");

    private final String label;

    AssetStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
