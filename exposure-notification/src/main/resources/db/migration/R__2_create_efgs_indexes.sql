drop index if exists en.diagnosis_key_efgs_sync;
create index diagnosis_key_efgs_sync on en.diagnosis_key(efgs_sync, retry_count, consent_to_share);

drop index if exists en.inbound_operation_updated_at_state;
create index inbound_operation_updated_at_state on en.efgs_inbound_operation(updated_at, state);

drop index if exists en.outbound_operation_updated_at_state;
create index outbound_operation_updated_at_state on en.efgs_outbound_operation(updated_at, state);
