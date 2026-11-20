package edu.citu.opc.fams.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A department or office, identified by its OPC code (e.g. MEDTECH, ME, OPC). Fully managed in Step 2. */
@Entity
@Table(name = "department")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "head_name", length = 100)
    private String headName;

    @Column(name = "head_designation", length = 60)
    private String headDesignation;

    protected Department() {
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getHeadName() { return headName; }
    public String getHeadDesignation() { return headDesignation; }
}
