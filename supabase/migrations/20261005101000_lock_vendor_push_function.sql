-- Lock send-fcm-vendor-native (verify_jwt off) to calls from this database.
-- Every pg_net call now carries an x-push-secret header read from Vault; the edge function checks it
-- with verify_internal_push_secret (service_role only) and rejects anything else with 401.
-- The secret value is generated here and lives only in Vault.
do $$
begin
  if not exists (select 1 from vault.secrets where name = 'internal_push_secret') then
    perform vault.create_secret(replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', ''), 'internal_push_secret',
      'Shared secret for DB -> send-fcm-vendor-native calls');
  end if;
end $$;

create or replace function public.internal_push_headers()
returns jsonb
language sql
stable
security definer
set search_path to 'public'
as $$
  select jsonb_build_object(
    'Content-Type', 'application/json',
    'apikey', 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inplbm5tb3VnaGZoZW5ud2dlY2dvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODcwNjI3MjksImV4cCI6MjEwMjYzODcyOX0.xSuD8sYQ4PWgN-YhOaHhwg6hdZYjw2_Jj-XrK3lMERc',
    'Authorization', 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inplbm5tb3VnaGZoZW5ud2dlY2dvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODcwNjI3MjksImV4cCI6MjEwMjYzODcyOX0.xSuD8sYQ4PWgN-YhOaHhwg6hdZYjw2_Jj-XrK3lMERc',
    'x-push-secret', (select decrypted_secret from vault.decrypted_secrets where name = 'internal_push_secret' limit 1)
  );
$$;

revoke all on function public.internal_push_headers() from public, anon, authenticated;

create or replace function public.verify_internal_push_secret(p_secret text)
returns boolean
language sql
stable
security definer
set search_path to 'public'
as $$
  select coalesce(p_secret, '') <> ''
     and exists (select 1 from vault.decrypted_secrets where name = 'internal_push_secret' and decrypted_secret = p_secret);
$$;

revoke all on function public.verify_internal_push_secret(text) from public, anon, authenticated;
grant execute on function public.verify_internal_push_secret(text) to service_role;

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
        headers := public.internal_push_headers(),
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

create or replace function public.auto_send_vendor_fcm_push()
returns trigger
language plpgsql
security definer
set search_path to 'public'
as $$
declare
  v_vendor_id uuid;
  v_token text;
  v_firebase_project text;
  v_function_url text;
  v_payload jsonb;
begin
  if new.user_type = 'vendor' then
    select v.id into v_vendor_id from public.vendors v where v.user_id = new.user_id limit 1;
    if v_vendor_id is null then return new; end if;

    select fcm_token, firebase_project into v_token, v_firebase_project
    from public.vendor_fcm_tokens
    where vendor_id = v_vendor_id::text and is_active = true
    order by updated_at desc nulls last limit 1;
    if v_token is null then return new; end if;

    v_function_url := case
      when v_firebase_project = 'native_vendor' then 'https://zennmoughfhennwgecgo.supabase.co/functions/v1/send-fcm-vendor-native'
      else 'https://zennmoughfhennwgecgo.supabase.co/functions/v1/send-fcm-order-alert'
    end;

    v_payload := coalesce(new.data, '{}'::jsonb) || jsonb_build_object('token', v_token, 'title', new.title, 'message', new.body);

    perform net.http_post(url := v_function_url, headers := public.internal_push_headers(), body := v_payload);

  elsif new.user_type in ('customer', 'delivery_partner', 'city_admin') then
    select fcm_token, firebase_project into v_token, v_firebase_project
    from public.device_tokens
    where user_id = new.user_id::text and user_type = new.user_type and active = true
    order by updated_at desc nulls last limit 1;
    if v_token is null then return new; end if;

    v_function_url := case
      when v_firebase_project = 'native' then 'https://zennmoughfhennwgecgo.supabase.co/functions/v1/send-fcm-vendor-native'
      else 'https://zennmoughfhennwgecgo.supabase.co/functions/v1/send-fcm-order-alert'
    end;

    v_payload := coalesce(new.data, '{}'::jsonb) || jsonb_build_object('token', v_token, 'title', new.title, 'message', new.body);

    perform net.http_post(url := v_function_url, headers := public.internal_push_headers(), body := v_payload);
  end if;

  return new;
end;
$$;

revoke all on function public.register_my_vendor_device_token(text, text, text) from public, anon;
grant execute on function public.register_my_vendor_device_token(text, text, text) to authenticated;
