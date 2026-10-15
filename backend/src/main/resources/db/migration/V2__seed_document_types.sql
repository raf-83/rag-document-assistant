INSERT INTO document_types (
    id,
    created_at,
    updated_at,
    code,
    name,
    description,
    active
)
VALUES
(
    '11111111-1111-1111-1111-111111111111',
    NOW(),
    NOW(),
    'INVOICE',
    'Faktura',
    'Dokument księgowy potwierdzający sprzedaż lub zakup.',
    TRUE
),
(
    '22222222-2222-2222-2222-222222222222',
    NOW(),
    NOW(),
    'CONTRACT',
    'Umowa',
    'Dokument zawierający ustalenia między stronami.',
    TRUE
),
(
    '33333333-3333-3333-3333-333333333333',
    NOW(),
    NOW(),
    'REPORT',
    'Raport',
    'Dokument zawierający zestawienie informacji, wyników lub analiz.',
    TRUE
),
(
    '44444444-4444-4444-4444-444444444444',
    NOW(),
    NOW(),
    'MEDICAL',
    'Dokument medyczny',
    'Dokument związany z leczeniem, badaniami lub historią zdrowia.',
    TRUE
),
(
    '55555555-5555-5555-5555-555555555555',
    NOW(),
    NOW(),
    'OFFICIAL',
    'Dokument urzędowy',
    'Dokument wydany przez urząd lub instytucję publiczną.',
    TRUE
),
(
    '66666666-6666-6666-6666-666666666666',
    NOW(),
    NOW(),
    'OTHER',
    'Inny',
    'Pozostały typ dokumentu, który nie pasuje do innych kategorii.',
    TRUE
);
