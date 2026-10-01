-- Single-device login for the food vendor app (applied to production on 2026-10-01).
-- Each install has a device_id; the vendor's one vendor_fcm_tokens row records the active device.
alter table public.vendor_fcm_tokens add column if not exists device_id text;

drop function if exists public.register_my_vendor_device_token(text, text);

create or replace function public.register_my_vendor_device_token(
  p_token text,
  p_email text default null,
  p_device_id text default null
)
returns boolean
language plpgsql
security definer
set search_path to 'public'
as $$
declare
  v_vendor_id uuid;
  v_old public.vendor_fcm_tokens;
  v_anon_key text := 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inplbm5tb3VnaGZoZW5ud2dlY2dvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODcwNjI3MjksImV4cCI6MjEwMjYzODcyOX0.xSuD8sYQ4PWgN-YhOaHhwg6hdZYjw2_Jj-XrK3lMERc';
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

  select * into v_old from public.vendor_fcm_tokens where vendor_id = v_vendor_id::text;

  -- A phone token can belong to one vendor only (unique index on fcm_token).
  delete from public.vendor_fcm_tokens
  where fcm_token = p_token and vendor_id <> v_vendor_id::text;

  insert into public.vendor_fcm_tokens (vendor_id, vendor_type, vendor_email, fcm_token, platform, is_active, firebase_project, device_id, updated_at)
  values (v_vendor_id::text, 'hotel', p_email, p_token, 'android', true, 'native_vendor', p_device_id, now())
  on conflict (vendor_id) do update
    set fcm_token = excluded.fcm_token,
        vendor_type = 'hotel',
        vendor_email = coalesce(excluded.vendor_email, public.vendor_fcm_tokens.vendor_email),
        platform = 'android',
        is_active = true,
        firebase_project = 'native_vendor',
        device_id = excluded.device_id,
        updated_at = now();

  -- Another phone was active for this vendor: tell it to log out (data-only push).
  if v_old.id is not null
     and v_old.is_active
     and v_old.fcm_token <> p_token
     and v_old.firebase_project = 'native_vendor'
     and v_old.device_id is distinct from p_device_id then
    begin
      perform net.http_post(
        url := 'https://zennmoughfhennwgecgo.supabase.co/functions/v1/send-fcm-vendor-native',
        headers := jsonb_build_object('Content-Type', 'application/json', 'apikey', v_anon_key, 'Authorization', 'Bearer ' || v_anon_key),
        body := jsonb_build_object(
          'token', v_old.fcm_token,
          'order_id', 'force_logout',
          'title', 'Logged out',
          'message', 'Your Sndmart Vendor account was opened on another phone.'
        )
      );
    exception when others then
      raise warning 'force logout push failed: %', sqlerrm;
    end;
  end if;

  return true;
end;
$$;

revoke all on function public.register_my_vendor_device_token(text, text, text) from public, anon;
grant execute on function public.register_my_vendor_device_token(text, text, text) to authenticated;

-- False when another phone is the active device for the logged-in vendor.
create or replace function public.is_my_vendor_device_active(p_device_id text)
returns boolean
language sql
stable
security definer
set search_path to 'public'
as $$
  select not exists (
    select 1
    from public.vendor_fcm_tokens t
    join public.vendors v on v.id::text = t.vendor_id
    where v.user_id = auth.uid()
      and t.is_active
      and t.device_id is not null
      and t.device_id is distinct from p_device_id
  );
$$;

revoke all on function public.is_my_vendor_device_active(text) from public, anon;
grant execute on function public.is_my_vendor_device_active(text) to authenticated;
