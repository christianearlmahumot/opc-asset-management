package edu.citu.opc.fams.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An accountable person: the employee named on an issue order and on the Property Inventory Slip.
 * Maintained by OPC staff; no HR data is imported (SRS 2.5). Personal data under the Data Privacy Act.
 */
@Entity
@Table(name = "employee")
public class Employee {

    public enum Status { ACTIVE, RESIGNED, INACTIVE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_no", unique = true, length = 20)
    private String employeeNo;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "department_id")
    private Long departmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    protected Employee() {
    }

    public Employee(String employeeNo, String fullName, Long departmentId) {
        this.employeeNo = employeeNo;
        this.fullName = fullName;
        this.departmentId = departmentId;
    }

    Map<String, Object> auditValues() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("employeeNo", employeeNo);
        m.put("fullName", fullName);
        m.put("departmentId", departmentId);
        m.put("status", status.name());
        return m;
    }

    public Long getId() { return id; }
    public String getEmployeeNo() { return employeeNo; }
    public String getFullName() { return fullName; }
    public Long getDepartmentId() { return departmentId; }
    public Status getStatus() { return status; }
    public boolean isActive() { return status == Status.ACTIVE; }

    void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }
    void setFullName(String fullName) { this.fullName = fullName; }
    void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    void setStatus(Status status) { this.status = status; }
}
