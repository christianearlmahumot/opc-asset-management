package edu.citu.opc.fams.asset;

import edu.citu.opc.fams.movement.Movement;
import edu.citu.opc.fams.movement.TurnoverReason;
import edu.citu.opc.fams.reference.Department;
import edu.citu.opc.fams.reference.Employee;

import java.util.Map;

/** Turns a movement row into one readable line, e.g. "MEDTECH · Phlebotomy Lab → MEDTECH · Storage Room". */
public final class MovementText {

    private MovementText() {
    }

    public static String summary(Movement m, Map<Long, Department> depts, Map<Long, Employee> emps) {
        String to = place(m.getToDepartmentId(), m.getToRoom(), m.getToEmployeeId(), depts, emps);
        String from = place(m.getFromDepartmentId(), m.getFromRoom(), m.getFromEmployeeId(), depts, emps);
        String toOrStorage = to.isEmpty() ? "OPC storage" : to;
        String condition = m.getConditionAfter() == null ? null : AssetCondition.valueOf(m.getConditionAfter()).label();
        String reason = m.getReason() == null ? null : TurnoverReason.valueOf(m.getReason()).label();
        String text = switch (m.getType()) {
            case ENCODE -> join(m.getNote(), toOrStorage);
            case CONDITION -> join(condition, m.getNote());
            case ISSUE -> "to " + to;
            case TRANSFER -> join(from + " → " + to, m.getNote());
            case TURNOVER -> join(join(from + " → OPC storage", reason),
                    join(condition != null && condition.equals(reason) ? null : condition, m.getNote()));
            case RESIGNATION -> join(from + " → " + toOrStorage, condition);
            case DISPOSAL -> join(m.getDisposalDate() == null ? null : "on " + m.getDisposalDate(), m.getNote());
        };
        return text == null || text.isBlank() ? m.getType().label() : text;
    }

    private static String place(Long deptId, String room, Long empId, Map<Long, Department> depts,
                                Map<Long, Employee> emps) {
        StringBuilder sb = new StringBuilder();
        if (deptId != null && depts.containsKey(deptId)) sb.append(depts.get(deptId).getCode());
        if (room != null) sb.append(sb.isEmpty() ? "" : " · ").append(room);
        if (empId != null && emps.containsKey(empId)) {
            sb.append(sb.isEmpty() ? "" : " · ").append(emps.get(empId).getFullName());
        }
        return sb.toString();
    }

    private static String join(String a, String b) {
        if (a == null || a.isBlank()) return b;
        if (b == null || b.isBlank()) return a;
        return a + " · " + b;
    }
}
