-- Registers this phone's FCM token for the logged-in food vendor (applied to production on 2026-10-01).
-- A phone token can belong to only one vendor (unique index vendor_fcm_tokens_token_uidx), so the token is
-- first removed from any other vendor that used this phone before; then the vendor's single row
-- (unique on vendor_id) is upserted and activated for the native vendor Firebase project.
create or replace function public.register_my_vendor_device_token(p_token text, p_email text default null)
returns boolean
language plpgsql
security definer
set search_path to 'public'
as $$
declare
  v_vendor_id uuid;
begin
  if nullif(trim(p_token), '') is null then
    raise exception 'FCM token required';
  end if;

  select id into v_vendor_id
  from public.vendors
  where user_id = auth.uid() and vendor_type = 'hotel'::public.vendor_type and is_active = true
  limit 1;
  if v_vendor_id is null then
    raise exception 'Food vendor not found';
  end if;

  delete from public.vendor_fcm_tokens
  where fcm_token = p_token and vendor_id <> v_vendor_id::text;

  insert into public.vendor_fcm_tokens (vendor_id, vendor_type, vendor_email, fcm_token, platform, is_active, firebase_project, updated_at)
  values (v_vendor_id::text, 'hotel', p_email, p_token, 'android', true, 'native_vendor', now())
  on conflict (vendor_id) do update
    set fcm_token = excluded.fcm_token,
        vendor_type = 'hotel',
        vendor_email = coalesce(excluded.vendor_email, public.vendor_fcm_tokens.vendor_email),
        platform = 'android',
        is_active = true,
        firebase_project = 'native_vendor',
        updated_at = now();

  return true;
end;
$$;

revoke all on function public.register_my_vendor_device_token(text, text) from public, anon;
grant execute on function public.register_my_vendor_device_token(text, text) to authenticated;
