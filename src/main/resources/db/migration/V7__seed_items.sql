-- Catalog seed: one row per model, colour and size (that's what an item is).
-- price is in minor units (cents) of currency; reserved_units starts at 0 (nothing in carts).
-- Middle sizes sell best, so they get the most stock; edge sizes get a few pairs.
INSERT INTO shop.items (id, name, colour, model, size, units_number, reserved_units, price, currency)
SELECT gen_random_uuid(),
       m.name,
       m.colour,
       m.model,
       s.size,
       CASE
           WHEN s.size BETWEEN m.min_size + 2 AND m.max_size - 2 THEN m.base_stock
           WHEN s.size BETWEEN m.min_size + 1 AND m.max_size - 1 THEN m.base_stock / 2
           ELSE 2
       END,
       0,
       m.price,
       m.currency
FROM (VALUES
          -- name                     colour      model        min max stock  price  currency
          ('Trail Runner',            'black',    'TR-100',     40, 46, 12,  12990, 'EUR'),
          ('Trail Runner',            'orange',   'TR-100',     40, 46,  8,  12990, 'EUR'),
          ('Urban Sneaker',           'white',    'US-200',     36, 45, 20,   8990, 'EUR'),
          ('Urban Sneaker',           'navy',     'US-200',     36, 45, 14,   8990, 'EUR'),
          ('Court Classic',           'white',    'CC-310',     38, 46, 16,   9990, 'EUR'),
          ('Leather Chelsea Boot',    'brown',    'LCB-410',    39, 46,  6,  18990, 'EUR'),
          ('Leather Chelsea Boot',    'black',    'LCB-410',    39, 46,  8,  18990, 'EUR'),
          ('Hiking Boot Pro',         'olive',    'HBP-520',    39, 47,  6,  21990, 'EUR'),
          ('Canvas Low',              'red',      'CL-130',     35, 45, 18,   4990, 'EUR'),
          ('Canvas Low',              'beige',    'CL-130',     35, 45, 18,   4990, 'EUR'),
          ('Ballet Flat',             'nude',     'BF-610',     35, 41, 10,   6490, 'EUR'),
          ('City Loafer',             'tan',      'CLF-720',    39, 46,  7,  14990, 'EUR'),
          ('Running Lite',            'grey',     'RL-150',     38, 46, 15,  10990, 'USD'),
          ('Winter Snow Boot',        'white',    'WSB-830',    36, 44,  5, 349900, 'UAH'),
          ('Slip-On Knit',            'black',    'SOK-240',    36, 45, 12,   5990, 'GBP')
     ) AS m(name, colour, model, min_size, max_size, base_stock, price, currency)
CROSS JOIN LATERAL generate_series(m.min_size, m.max_size) AS s(size);
