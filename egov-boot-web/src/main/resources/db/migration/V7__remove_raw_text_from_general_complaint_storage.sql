-- Existing deployments cannot retroactively move plaintext into the encrypted
-- payload store. Remove it from the generally accessible complaint row and
-- retain only the already-generated redacted derivative.
update complaints
set raw_text = redacted_text
where raw_text <> redacted_text;
