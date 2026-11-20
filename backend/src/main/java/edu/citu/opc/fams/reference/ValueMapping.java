package edu.citu.opc.fams.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Maps a messy spelling to its clean value, e.g. unit "pcs" → "pc" or department "Med Tech" → "MEDTECH".
 * Used by encoding now and by the masterlist import later (UC-107).
 */
@Entity
@Table(name = "value_mapping")
public class ValueMapping {

    public enum Kind { DEPARTMENT, UNIT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(name = "source_value", nullable = false)
    private String sourceValue;

    @Column(name = "mapped_value", nullable = false, length = 60)
    private String mappedValue;

    protected ValueMapping() {
    }

    public ValueMapping(Kind kind, String sourceValue, String mappedValue) {
        this.kind = kind;
        this.sourceValue = sourceValue;
        this.mappedValue = mappedValue;
    }

    public Long getId() { return id; }
    public Kind getKind() { return kind; }
    public String getSourceValue() { return sourceValue; }
    public String getMappedValue() { return mappedValue; }
}
