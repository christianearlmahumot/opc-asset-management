-- Default categories (SRS 2.5: the CIT-U purchase request categories).
INSERT INTO category (name) VALUES
    ('CAPEX-Equipment'),
    ('CAPEX-Furniture and Fixtures'),
    ('Inventoriable Items');

-- The OPC itself always exists. Other departments come from the Administrators sheet of the masterlist workbook.
INSERT INTO department (code, name, head_name, head_designation) VALUES
    ('OPC', 'Office of the Property Custodian', NULL, 'Property Custodian');

-- Unit spellings seen in the masterlist (SRS 2.1), so they normalize from day one.
INSERT INTO value_mapping (kind, source_value, mapped_value) VALUES
    ('UNIT', 'pcs', 'pc'), ('UNIT', 'pc', 'pc'), ('UNIT', 'piece', 'pc'), ('UNIT', 'pieces', 'pc'),
    ('UNIT', 'copy', 'copy'), ('UNIT', 'copies', 'copy'),
    ('UNIT', 'unit', 'unit'), ('UNIT', 'units', 'unit'),
    ('UNIT', 'set', 'set'), ('UNIT', 'sets', 'set'),
    ('UNIT', 'lot', 'lot'), ('UNIT', 'box', 'box'), ('UNIT', 'boxes', 'box');
