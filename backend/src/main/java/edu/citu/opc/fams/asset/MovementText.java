package edu.citu.opc.fams.asset;

import edu.citu.opc.fams.movement.Movement;
import edu.citu.opc.fams.reference.Department;
import edu.citu.opc.fams.reference.Employee;

import java.util.Map;

/** Turns a movement row into the one-line text of the history panel, e.g. "Issued · IO 1700277 to OPC". */
final class MovementText {

    private MovementText() {
    }

    static String summary(Movement m, Map<Long, Department> depts, Map<Long, Employee> emps) {
        String to = place(m.getToDepartmentId(), m.getToRoom(), m.getToEmployeeId(), depts, emps);
        String from = place(m.getFromDepartmentId(), m.getFromRoom(), m.getFromEmployeeId(), depts, emps);
        String condition = m.getConditionAfter() == null ? null : AssetCondition.valueOf(m.getConditionAfter()).label();
        String text = switch (m.getType()) {
            case ENCODE -> join(m.getNote(), to.isEmpty() ? "OPC storage" : to);
            case CONDITION -> condition;
            case ISSUE -> "to " + to;
            case TRANSFER -> from + " → " + to;
            case TURNOVER, RESIGNATION -> join(from + " → " + (to.isEmpty() ? "OPC storage" : to),
                    m.getReason() == null ? null : reasonLabel(m.getReason()));
            case DISPOSAL -> m.getDisposalDate() == null ? null : "on " + m.getDisposalDate();
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

    static String reasonLabel(String reason) {
        return switch (reason) {
            case "DEFECTIVE" -> "Defective";
            case "DAMAGED" -> "Damaged";
            case "NO_LONGER_USED" -> "No longer used";
            case "FOR_UPGRADE" -> "For upgrade";
            default -> "Other";
        };
    }
}
