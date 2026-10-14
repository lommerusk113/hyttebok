-- Ground height at the cabin (metres above sea level), so the weather is adjusted for it. Looked up with the coordinates.
alter table cabin_info add column elevation double precision;
