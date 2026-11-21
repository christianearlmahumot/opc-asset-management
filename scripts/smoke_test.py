"""
End-to-end smoke test of the OPC Fixed-Asset Management System API.

Exercises every finished use case and the role rules of SRS 2.2 (REQ-403, REQ-406) against a
running back end, and prints PASS / FAIL per check. Uses only the Python standard library.

    python scripts/smoke_test.py                       # local back end (http://localhost:8080)
    python scripts/smoke_test.py https://opc-fams-api.onrender.com

It asks for an Administrator's password (never stored or printed). It CREATES test records whose
names start with "ZTEST", uses one issue order number, and leaves four deactivated test users.
The system has no delete (by design: audit, REQ-404), so run it only on a test database.
"""
import getpass
import json
import os
import secrets
import string
import sys
import tempfile
import urllib.error
import urllib.request
from datetime import date

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080").rstrip("/")
TODAY = date.today().isoformat()
SFX = "".join(secrets.choice(string.ascii_uppercase + string.digits) for _ in range(5))
results = []


def call(method, path, token=None, body=None, raw=False):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Accept", "application/json")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            content = r.read()
            status = r.status
    except urllib.error.HTTPError as e:
        content = e.read()
        status = e.code
    if raw:
        return status, content
    try:
        return status, json.loads(content) if content else None
    except ValueError:
        return status, content[:200]


def check(name, ok, detail=""):
    results.append((bool(ok), name, detail))
    print(("PASS  " if ok else "FAIL  ") + name + ("" if ok or not detail else "  -> " + str(detail)[:300]))
    return bool(ok)


def section(title):
    print("\n== " + title)


def pw():
    return "Zt-" + secrets.token_urlsafe(12)


def main():
    print(f"Target: {BASE}   test suffix: {SFX}")
    admin_user = input("Administrator username [admin]: ").strip() or "admin"
    admin_pw = getpass.getpass("Administrator password (hidden): ")

    section("Access control: login (UC-401)")
    s, b = call("GET", "/api/health")
    check("health check answers", s == 200 and b and b.get("status") == "ok", (s, b))
    s, b = call("POST", "/api/auth/login", body={"username": admin_user, "password": "wrong-" + SFX})
    check("wrong password refused with a generic message", s == 401 and "Invalid username or password" in str(b), (s, b))
    s, b = call("POST", "/api/auth/login", body={"username": "nobody" + SFX, "password": "x" * 10})
    check("unknown username gets the same message", s == 401 and "Invalid username or password" in str(b), (s, b))
    s, b = call("GET", "/api/assets")
    check("API refuses calls without a token", s == 401, s)
    s, b = call("POST", "/api/auth/login", body={"username": admin_user, "password": admin_pw})
    if not check("administrator logs in", s == 200 and b and b.get("token"), (s, b)):
        return
    A = b["token"]
    if b["user"].get("mustChangePassword"):
        check("administrator has no temporary password (change it in the app first)", False)
        return
    s, b = call("GET", "/api/auth/me", A)
    check("token carries the Administrator role", s == 200 and b["role"] == "ADMIN", b)

    section("Reference data")
    s, b = call("POST", "/api/departments", A, {"code": "ZT" + SFX, "name": "ZTEST Department " + SFX,
                                               "headName": "ZTEST Head " + SFX, "headDesignation": "Chair"})
    if not check("create department", s == 201, (s, b)):
        return
    DEPT = b["id"]
    s, deps = call("GET", "/api/departments", A)
    OPC = next((d["id"] for d in deps if d["code"] == "OPC"), None)
    check("OPC department exists", OPC is not None)
    s, b = call("POST", "/api/employees", A, {"employeeNo": "ZT" + SFX, "fullName": "ZTEST Person " + SFX,
                                             "departmentId": DEPT})
    if not check("create accountable person in the department", s == 201, (s, b)):
        return
    EMP = b["id"]
    s, cats = call("GET", "/api/categories", A)
    check("categories listed (3 defaults seeded)", s == 200 and len(cats) >= 3, cats)
    CAT = cats[0]["id"]
    s, units = call("GET", "/api/units", A)
    check("units listed", s == 200 and "unit" in units, units)

    def asset(desc, qty=1, cost="1000.00", **kw):
        body = {"description": desc, "categoryId": CAT, "quantity": qty, "unit": "units", "acquisitionDate": TODAY,
                "unitCost": cost, "condition": "GOOD", "supplier": "ZTEST Supplier", "invoiceNo": "ZT" + SFX,
                "confirmDuplicate": False}
        body.update(kw)
        return body

    section("Asset register (UC-104, UC-106, UC-205)")
    s, b = call("POST", "/api/assets", A, {**asset(""), "description": ""})
    check("missing description is refused and highlighted", s == 400 and "description" in (b or {}).get("fields", {}), (s, b))
    s, b = call("GET", f"/api/assets/tag-suggestion?description=ZTEST%20Printer", A)
    check("property tag suggested in year-office-type-number form", s == 200 and b["tag"].count("-") == 3, b)
    s, b = call("POST", "/api/assets", A, asset("ZTEST Printer " + SFX, cost="10000.00"))
    if not check("add asset (goes to OPC storage)", s == 201 and b["status"] == "IN_STORAGE", (s, b)):
        return
    A1 = b["id"]
    check("unit normalized (units -> unit)", b["unit"] == "unit", b["unit"])
    s, b = call("POST", "/api/assets", A, asset("ZTEST Printer " + SFX))
    check("duplicate warning on the same item (UC-104 5b)", s == 409 and b["details"]["canOverride"] is True, (s, b))
    s, b = call("POST", "/api/assets", A, asset("ZTEST Stool " + SFX, qty=5, cost="500.00"))
    check("add a quantity item (5 stools)", s == 201, (s, b))
    A2 = b["id"]
    s, b = call("PUT", f"/api/assets/{A1}", A, asset("ZTEST Printer " + SFX, cost="10000.00", brand="Canon"))
    check("edit asset details", s == 200 and b["brand"] == "Canon", (s, b))
    s, b = call("PUT", f"/api/assets/{A1}", A, asset("ZTEST Printer " + SFX, cost="10000.00", brand="Canon",
                                                    departmentId=DEPT))
    check("edit refuses a department change (UC-106 3a: record a transfer)", s == 400, (s, b))
    s, b = call("GET", f"/api/assets?q=ZTEST%20Printer%20{SFX}", A)
    check("search finds the asset", s == 200 and any(r["id"] == A1 for r in b["items"]), b)
    s, b = call("GET", f"/api/assets/{A1}/history", A)
    check("history starts with ENCODE", s == 200 and b and b[-1]["type"] == "ENCODE", b)

    section("Issue orders (UC-201, UC-202, UC-204)")
    s, b = call("POST", "/api/requests", A, {"departmentId": DEPT, "prNo": "PR-ZT-" + SFX, "purpose": "ZTEST",
                                            "items": [{"description": "printer", "quantity": 1}]})
    check("record department request (Pending)", s == 201 and b["status"] == "PENDING", (s, b))
    REQ = b["id"]
    s, b = call("POST", "/api/requests", A, {"departmentId": DEPT, "items": []})
    check("request without items is refused", s == 400, (s, b))
    s, b = call("POST", "/api/issue-orders", A, {"requestId": REQ, "employeeId": EMP, "room": "ZT Room A",
                                                "lines": [{"assetId": A1}, {"assetId": A2}]})
    if not check("generate issue order", s == 201 and len(b["ioNo"]) == 7 and b["ioNo"].isdigit(), (s, b)):
        return
    IO, IONO = b["id"], b["ioNo"]
    print(f"      (used issue order number {IONO})")
    s, b = call("GET", f"/api/requests/{REQ}", A)
    check("request marked Fulfilled", b["status"] == "FULFILLED", b)
    s, b = call("GET", f"/api/assets/{A1}", A)
    check("asset assigned to the department and person", b["status"] == "ISSUED" and b["departmentId"] == DEPT
          and b["employeeId"] == EMP and b["issueOrderNo"] == IONO, b)
    s, pdf = call("GET", f"/api/issue-orders/{IO}/pdf", A, raw=True)
    check("issue order PDF generated (reprint)", s == 200 and pdf[:4] == b"%PDF", s)
    s, b = call("GET", f"/api/issue-orders?ioNo={IONO}", A)
    check("issue order history filter by number", s == 200 and any(r["id"] == IO for r in b["items"]), b)
    s, b = call("GET", f"/api/assets?q={IONO}", A)
    check("asset search by issue order number", s == 200 and {A1, A2} <= {r["id"] for r in b["items"]}, b)
    s, b = call("POST", "/api/issue-orders", A, {"departmentId": DEPT, "employeeId": EMP, "lines": [{"assetId": A1}]})
    check("an already issued asset cannot be issued again (UC-202 5a)", s == 409, (s, b))

    section("Movements (UC-301, UC-302, UC-303)")
    s, b = call("POST", "/api/movements/transfer", A, {"assetId": A2, "toDepartmentId": DEPT, "toRoom": "ZT Room B",
                                                      "toEmployeeId": EMP, "quantity": 2, "reason": "ZTEST split"})
    check("partial transfer splits the item (2 of 5)", s == 200 and b["split"] is True, (s, b))
    A2B = b.get("assetId")
    s, b = call("GET", f"/api/assets/{A2}", A)
    check("source keeps the remaining quantity (3)", b["quantity"] == 3, b)
    s, b = call("POST", "/api/movements/turnover", A, {"assetId": A1, "condition": "DEFECTIVE", "action": "KEEP"})
    check("condition change kept in department", s == 200, (s, b))

    section("Department inventory report (UC-304)")
    s, b = call("GET", f"/api/reports/departments/{DEPT}/summary", A)
    ok = s == 200
    if ok:
        rooms = {g["room"] for g in b["rooms"]}
        ok = b["itemCount"] == 3 and b["totalQuantity"] == 6 and float(b["totalCost"]) == 12500.0 \
            and {"ZT Room A", "ZT Room B"} <= rooms
    check("report groups by room with correct totals (3 items, qty 6, 12,500.00)", ok, (s, b))
    s, b = call("GET", f"/api/reports/departments/{OPC}/summary", A)
    check("OPC report includes OPC storage", s == 200, (s, b))

    section("Turnover, disposal, resignation")
    s, b = call("POST", "/api/movements/turnover", A, {"assetId": A1, "condition": "DAMAGED", "action": "TURN_OVER",
                                                      "reason": "DAMAGED", "dateReturned": TODAY,
                                                      "turnedOverBy": "ZTEST"})
    check("turn over to OPC storage", s == 200, (s, b))
    s, b = call("POST", "/api/movements/disposal", A, {"assetId": A1, "disposalDate": TODAY})
    check("record disposal date", s == 200, (s, b))
    s, b = call("GET", f"/api/assets/{A1}", A)
    check("asset is now Disposed", b["status"] == "DISPOSED", b)
    s, held = call("GET", f"/api/employees/{EMP}/assets", A)
    check("resignation lists the person's items (2)", s == 200 and len(held) == 2, held)
    s, b = call("POST", "/api/movements/resignation", A, {"employeeId": EMP, "markResigned": True, "items": [
        {"assetId": h["id"], "toEmployeeId": None, "condition": "GOOD"} for h in held]})
    check("resignation turnover: every item returned, person marked resigned",
          s == 200 and b["completed"] == 2 and b["stillHeld"] == 0 and b["markedResigned"], (s, b))
    s, b = call("GET", f"/api/assets/{A2}/history", A)
    types = [h["type"] for h in b] if s == 200 else []
    check("history shows every movement", {"ENCODE", "ISSUE", "TRANSFER", "RESIGNATION"} <= set(types), types)

    section("Audit log (UC-404, REQ-404)")
    s, b = call("GET", f"/api/audit-logs?entity=asset&entityId={A1}", A)
    check("asset changes are in the audit log", s == 200 and b["total"] >= 4, (s, b))
    s, b = call("GET", f"/api/audit-logs?entity=dept_request&entityId={REQ}", A)
    check("request fulfilment is audited",
          s == 200 and any((e.get("newValues") or {}).get("status") == "FULFILLED" for e in b["items"]), b)
    s, b = call("GET", "/api/audit-logs?action=LOGIN_FAILED", A)
    check("failed logins are audited", s == 200 and b["total"] >= 1, b)
    s, b = call("GET", "/api/audit-logs?from=2026-12-31&to=2026-01-01", A)
    check("reversed date range refused", s == 400, (s, b))

    section("Roles (SRS 2.2, REQ-403, REQ-406)")
    users = {}
    for key, role, dept in [("staff", "STAFF", None), ("nas1", "NAS", None), ("nas2", "NAS", None),
                            ("head", "DEPT_HEAD", DEPT)]:
        temp = pw()
        s, b = call("POST", "/api/users", A, {"fullName": f"ZTEST {key} {SFX}", "username": f"zt{key}{SFX}".lower(),
                                             "temporaryPassword": temp, "role": role, "departmentId": dept})
        if not check(f"create {role} user", s == 201, (s, b)):
            return
        uid = b["id"]
        s, b = call("POST", "/api/auth/login", body={"username": f"zt{key}{SFX}".lower(), "password": temp})
        tok = b["token"]
        s, _ = call("GET", "/api/assets", tok)
        check(f"{key}: temporary password blocks other calls", s == 403)
        s, b = call("POST", "/api/auth/change-password", tok, {"currentPassword": temp, "newPassword": pw()})
        check(f"{key}: changes temporary password", s == 200, (s, b))
        users[key] = (uid, b["token"])
    S, N1, N2, H = users["staff"][1], users["nas1"][1], users["nas2"][1], users["head"][1]

    s, b = call("POST", "/api/assets", N1, asset("ZTEST Draft " + SFX))
    check("NAS: new asset is saved as a draft", s == 201 and b["status"] == "DRAFT", (s, b))
    D1 = b.get("id")
    s, _ = call("PUT", f"/api/assets/{D1}", N2, asset("ZTEST Draft " + SFX, brand="X"))
    check("NAS: cannot edit another NAS's draft", s == 403, s)
    s, _ = call("PUT", f"/api/assets/{D1}", N1, asset("ZTEST Draft " + SFX, brand="X"))
    check("NAS: can edit own draft", s == 200, s)
    transfer = {"assetId": A2, "toDepartmentId": DEPT, "toRoom": "X", "toEmployeeId": EMP, "quantity": 1, "reason": "x"}
    for path, method, body in [("/api/issue-orders", "GET", None), ("/api/movements/transfer", "POST", transfer),
                               (f"/api/reports/departments/{DEPT}/summary", "GET", None),
                               ("/api/audit-logs", "GET", None), ("/api/users", "GET", None)]:
        s, _ = call(method, path, N1, body)
        check(f"NAS: refused {method} {path.split('?')[0]}", s == 403, s)

    s, b = call("POST", f"/api/assets/{D1}/confirm", S)
    check("Staff: confirms the NAS draft", s == 200 and b["status"] == "IN_STORAGE", (s, b))
    s, _ = call("GET", f"/api/reports/departments/{DEPT}/summary", S)
    check("Staff: can view department report", s == 200, s)
    for path, method, body in [("/api/audit-logs", "GET", None), ("/api/users", "GET", None),
                               ("/api/categories", "POST", {"name": "ZTEST " + SFX})]:
        s, _ = call(method, path, S, body)
        check(f"Staff: refused {method} {path}", s == 403, s)

    s, _ = call("GET", f"/api/reports/departments/{DEPT}/summary", H)
    check("Dept Head: sees own department report", s == 200, s)
    s, _ = call("GET", f"/api/reports/departments/{OPC}/summary", H)
    check("Dept Head: another department's report is hidden (404)", s == 404, s)
    s, _ = call("GET", f"/api/assets/{D1}", H)
    check("Dept Head: an asset outside the department is hidden (404)", s == 404, s)
    s, b = call("GET", "/api/assets?size=100", H)
    check("Dept Head: asset search returns only own department",
          s == 200 and all(r["departmentId"] == DEPT for r in b["items"]), b)
    s, b = call("GET", "/api/employees", H)
    check("Dept Head: accountable persons only from own department",
          s == 200 and all(e["departmentId"] == DEPT for e in b), b)
    for path, method, body in [("/api/issue-orders", "GET", None), ("/api/assets", "POST", asset("ZTEST H " + SFX)),
                               ("/api/audit-logs", "GET", None)]:
        s, _ = call(method, path, H, body)
        check(f"Dept Head: refused {method} {path}", s == 403, s)

    section("Deactivation (UC-402)")
    s, _ = call("POST", f"/api/users/{users['nas2'][0]}/deactivate", A)
    check("deactivate a user", s == 200, s)
    s, _ = call("GET", "/api/auth/me", N2)
    check("deactivated user's session stops at once", s == 401, s)
    for key in ("staff", "nas1", "head"):
        call("POST", f"/api/users/{users[key][0]}/deactivate", A)
    print("      (test users deactivated)")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:  # report, do not hide, an unexpected crash
        check("test run finished without crashing", False, repr(e))
    passed = sum(1 for r in results if r[0])
    failed = [r for r in results if not r[0]]
    print(f"\nRESULT: {passed} passed, {len(failed)} failed")
    out = os.path.join(tempfile.gettempdir(), "fams-smoke-results.txt")
    with open(out, "w", encoding="utf-8") as f:
        f.write(f"target={BASE} suffix={SFX}\n")
        for ok, name, detail in results:
            f.write(("PASS  " if ok else "FAIL  ") + name + ("" if ok else "  -> " + str(detail)[:500]) + "\n")
        f.write(f"RESULT: {passed} passed, {len(failed)} failed\n")
    print(f"Saved to {out}")
