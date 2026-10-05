-- Anonymous callers could insert or update 'fruit' vendor push tokens (no such rows exist).
-- Vendors register tokens only through register_my_vendor_device_token while logged in.
drop policy if exists vendor_fcm_tokens_anon_insert on public.vendor_fcm_tokens;
drop policy if exists vendor_fcm_tokens_anon_update on public.vendor_fcm_tokens;
