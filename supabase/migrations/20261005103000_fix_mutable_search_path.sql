-- Security advisor 0011: pin search_path on functions that had none.
alter function public.haversine_km(numeric, numeric, numeric, numeric) set search_path = public, extensions;
alter function public.sync_veg_fields() set search_path = public, extensions;
alter function public.sync_veg_fields_insert() set search_path = public, extensions;
alter function public.calculate_delivery_eta_minutes(numeric, numeric, numeric, numeric, numeric, numeric, numeric, numeric) set search_path = public, extensions;
alter function public.find_city_for_location(numeric, numeric) set search_path = public, extensions;
