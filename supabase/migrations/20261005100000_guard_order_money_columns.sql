-- Vendors, delivery partners and customers may not change an order's money, payment or ownership
-- fields through the REST API. Before this, restrict_customer_order_update let any vendor patch its
-- own order's subtotal, which on_order_status_changed then credits to the vendor wallet on delivery.
-- Server functions (SECURITY DEFINER, owned by postgres), admins and the order's City Admin are
-- unaffected. SECURITY INVOKER so current_user is the API role on direct REST updates.
create or replace function public.guard_order_money_columns()
returns trigger
language plpgsql
security invoker
set search_path to 'public'
as $$
begin
  if public.is_privileged_writer()
     or (old.city_id is not null and public.is_city_admin_for(old.city_id))
     or coalesce(current_setting('sndmart.internal_checkout', true), 'false') = 'true' then
    return new;
  end if;

  if new.total_amount is distinct from old.total_amount
     or new.subtotal is distinct from old.subtotal
     or new.discount_amount is distinct from old.discount_amount
     or new.delivery_fee is distinct from old.delivery_fee
     or new.handling_fee is distinct from old.handling_fee
     or new.commission_amount is distinct from old.commission_amount
     or new.vendor_amount is distinct from old.vendor_amount
     or new.payment_status is distinct from old.payment_status
     or new.payment_method is distinct from old.payment_method
     or new.vendor_id is distinct from old.vendor_id
     or new.delivery_partner_id is distinct from old.delivery_partner_id
     or new.city_id is distinct from old.city_id
     or new.address_id is distinct from old.address_id
     or new.customer_id is distinct from old.customer_id then
    raise exception 'Order amounts, payment and assignment can only be changed by the server or an admin'
      using errcode = '42501';
  end if;

  return new;
end;
$$;

create trigger trg_guard_order_money_columns
  before update on public.orders
  for each row execute function public.guard_order_money_columns();

revoke all on function public.guard_order_money_columns() from public, anon, authenticated;
