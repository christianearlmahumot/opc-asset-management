package edu.citu.opc.fams.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.LinkedHashMap;
import java.util.Map;

/** A department or office, identified by its OPC code (e.g. MEDTECH, ME, OPC). */
@Entity
@Table(name = "department")
public class Department {

    /** The OPC itself: items "in OPC storage" belong here or to no department. */
    public static final String OPC_CODE = "OPC";

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

    public Department(String code, String name, String headName, String headDesignation) {
        this.code = code;
        this.name = name;
        this.headName = headName;
        this.headDesignation = headDesignation;
    }

    Map<String, Object> auditValues() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("headName", headName);
        m.put("headDesignation", headDesignation);
        return m;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getHeadName() { return headName; }
    public String getHeadDesignation() { return headDesignation; }

    void setCode(String code) { this.code = code; }
    void setName(String name) { this.name = name; }
    void setHeadName(String headName) { this.headName = headName; }
    void setHeadDesignation(String headDesignation) { this.headDesignation = headDesignation; }
}
