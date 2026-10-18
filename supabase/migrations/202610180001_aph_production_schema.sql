-- APH · AbdulmajeedPharma production schema
-- Apply with `supabase db push` to a new Supabase project.

create extension if not exists pgcrypto;
create extension if not exists pg_trgm;
create extension if not exists unaccent;

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  new.updated_at = now();
  if tg_table_name in ('drugs', 'products') then
    new.version = coalesce(old.version, 0) + 1;
  end if;
  return new;
end;
$$;

create table if not exists public.body_systems (
  id text primary key,
  name text not null,
  name_ar text not null,
  display_order integer not null unique,
  color text not null default '#0b5c63',
  icon text not null default 'Activity',
  created_at timestamptz not null default now()
);

insert into public.body_systems (id, name, name_ar, display_order, color, icon) values
  ('nervous', 'Nervous System', 'الجهاز العصبي', 1, '#7259a8', 'Brain'),
  ('cardiovascular', 'Cardiovascular System', 'جهاز القلب والأوعية', 2, '#d45161', 'HeartPulse'),
  ('blood', 'Blood and Hematopoietic System', 'الدم وتكوين الدم', 3, '#a83e58', 'Droplets'),
  ('respiratory', 'Respiratory System', 'الجهاز التنفسي', 4, '#3f87a6', 'Wind'),
  ('gastrointestinal', 'Gastrointestinal System', 'الجهاز الهضمي', 5, '#d47b39', 'Activity'),
  ('hepatobiliary', 'Hepatobiliary System', 'الكبد والقنوات الصفراوية', 6, '#a77935', 'Bean'),
  ('endocrine', 'Endocrine System', 'جهاز الغدد الصماء', 7, '#3b8f87', 'Orbit'),
  ('renal', 'Renal and Urinary System', 'الكلى والجهاز البولي', 8, '#6884bd', 'CircleDot'),
  ('reproductive', 'Reproductive System', 'الجهاز التناسلي', 9, '#b65e91', 'PersonStanding'),
  ('musculoskeletal', 'Musculoskeletal System', 'الجهاز العضلي الهيكلي', 10, '#99704b', 'Bone'),
  ('skin', 'Skin System', 'الجلد', 11, '#b9785a', 'ScanFace'),
  ('eye', 'Eye', 'العين', 12, '#4b8db5', 'Eye'),
  ('ent', 'Ear, Nose, and Throat', 'الأنف والأذن والحنجرة', 13, '#707cb4', 'Ear'),
  ('immune', 'Immune System', 'الجهاز المناعي', 14, '#4d9169', 'ShieldPlus'),
  ('antiinfective', 'Anti-infective System', 'مضادات العدوى', 15, '#168477', 'ShieldCheck'),
  ('oncology', 'Oncology', 'الأورام', 16, '#85629e', 'Ribbon'),
  ('emergency', 'Emergency and Toxicology', 'الطوارئ والسموم', 17, '#d35b42', 'Siren'),
  ('diagnostic', 'Diagnostic and Miscellaneous', 'التشخيص والأدوية المتنوعة', 18, '#63737b', 'Microscope')
on conflict (id) do update set
  name = excluded.name,
  name_ar = excluded.name_ar,
  display_order = excluded.display_order,
  color = excluded.color,
  icon = excluded.icon;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  email text unique,
  display_name text,
  role text not null default 'user' check (role in ('user', 'pharmacist', 'editor', 'admin')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, email, display_name, role)
  values (
    new.id,
    lower(new.email),
    coalesce(new.raw_user_meta_data ->> 'display_name', split_part(new.email, '@', 1)),
    'user'
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.profiles
    where id = auth.uid() and role in ('admin', 'editor')
  );
$$;

create or replace function public.protect_profile_security_fields()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  -- A user may edit their display name, but can never promote their own role
  -- or replace the email associated with the authenticated identity.
  if auth.uid() is not null and not public.is_admin() then
    new.role := old.role;
    new.email := old.email;
  end if;
  return new;
end;
$$;

create trigger protect_profile_security_fields
before update on public.profiles
for each row execute function public.protect_profile_security_fields();

create trigger set_profiles_updated_at
before update on public.profiles
for each row execute function public.set_updated_at();

create table if not exists public.drugs (
  id text primary key,
  generic_name text not null,
  arabic_name text not null default '',
  system_id text not null references public.body_systems(id),
  pharmacology_class text not null default 'Unclassified',
  pharmacology_class_ar text not null default 'غير مصنف',
  subclass text not null default '',
  subclass_ar text not null default '',
  aliases text[] not null default '{}',
  description text not null default '',
  description_ar text not null default '',
  mechanism text not null default '',
  mechanism_ar text not null default '',
  indications text[] not null default '{}',
  indications_ar text[] not null default '{}',
  cautions text[] not null default '{}',
  cautions_ar text[] not null default '{}',
  interactions text[] not null default '{}',
  dosage_note text not null default '',
  dosage_note_ar text not null default '',
  image_url text,
  source_refs jsonb not null default '[]'::jsonb,
  search_document tsvector not null default ''::tsvector,
  verification_status text not null default 'unverified' check (verification_status in ('unverified', 'reviewed', 'verified', 'rejected')),
  is_published boolean not null default false,
  version bigint not null default 1,
  created_by uuid references auth.users(id),
  updated_by uuid references auth.users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.products (
  id text primary key,
  drug_id text not null references public.drugs(id) on delete cascade,
  brand_name text not null,
  brand_name_ar text not null default '',
  generic_name text not null default '',
  strength text not null default '',
  dosage_form text not null default '',
  dosage_form_ar text not null default '',
  manufacturer text not null default '',
  country text not null default '',
  local_agent text not null default '',
  market text not null default '',
  barcode text not null default '',
  registration_number text not null default '',
  image_url text,
  leaflet_url text not null default '',
  search_document tsvector not null default ''::tsvector,
  verification_status text not null default 'unverified' check (verification_status in ('unverified', 'reviewed', 'verified', 'rejected')),
  is_published boolean not null default false,
  version bigint not null default 1,
  created_by uuid references auth.users(id),
  updated_by uuid references auth.users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create or replace function public.update_drug_search_document()
returns trigger
language plpgsql
set search_path = public, extensions
as $$
begin
  new.search_document := to_tsvector(
    'simple',
    coalesce(new.generic_name, '') || ' ' || coalesce(new.arabic_name, '') || ' ' ||
    coalesce(new.pharmacology_class, '') || ' ' || coalesce(new.pharmacology_class_ar, '') || ' ' ||
    array_to_string(coalesce(new.aliases, '{}'), ' ')
  );
  return new;
end;
$$;

create or replace function public.update_product_search_document()
returns trigger
language plpgsql
set search_path = public, extensions
as $$
begin
  new.search_document := to_tsvector(
    'simple',
    coalesce(new.brand_name, '') || ' ' || coalesce(new.brand_name_ar, '') || ' ' ||
    coalesce(new.generic_name, '') || ' ' || coalesce(new.manufacturer, '') || ' ' ||
    coalesce(new.country, '') || ' ' || coalesce(new.local_agent, '') || ' ' || coalesce(new.barcode, '')
  );
  return new;
end;
$$;

create trigger update_drug_search_document
before insert or update on public.drugs
for each row execute function public.update_drug_search_document();

create trigger update_product_search_document
before insert or update on public.products
for each row execute function public.update_product_search_document();

create index if not exists drugs_system_idx on public.drugs(system_id);
create index if not exists drugs_updated_idx on public.drugs(updated_at desc);
create index if not exists drugs_published_idx on public.drugs(is_published) where is_published;
create index if not exists drugs_generic_trgm_idx on public.drugs using gin (generic_name gin_trgm_ops);
create index if not exists drugs_arabic_trgm_idx on public.drugs using gin (arabic_name gin_trgm_ops);
create index if not exists drugs_aliases_idx on public.drugs using gin (aliases);
create index if not exists drugs_search_document_idx on public.drugs using gin (search_document);
create index if not exists products_drug_idx on public.products(drug_id);
create index if not exists products_updated_idx on public.products(updated_at desc);
create index if not exists products_brand_trgm_idx on public.products using gin (brand_name gin_trgm_ops);
create index if not exists products_brand_ar_trgm_idx on public.products using gin (brand_name_ar gin_trgm_ops);
create index if not exists products_barcode_idx on public.products(barcode) where barcode <> '';
create index if not exists products_manufacturer_idx on public.products(manufacturer);
create index if not exists products_country_idx on public.products(country);
create index if not exists products_agent_idx on public.products(local_agent);
create index if not exists products_search_document_idx on public.products using gin (search_document);

create trigger set_drugs_updated_at
before update on public.drugs
for each row execute function public.set_updated_at();

create trigger set_products_updated_at
before update on public.products
for each row execute function public.set_updated_at();

create table if not exists public.import_jobs (
  id uuid primary key default gen_random_uuid(),
  file_name text not null,
  storage_path text,
  file_type text not null,
  status text not null default 'queued' check (status in ('queued', 'processing', 'complete', 'partial', 'failed')),
  total_rows bigint not null default 0,
  imported_rows bigint not null default 0,
  skipped_rows bigint not null default 0,
  error_message text,
  created_by uuid references auth.users(id),
  created_at timestamptz not null default now(),
  completed_at timestamptz
);

create table if not exists public.messages (
  id uuid primary key default gen_random_uuid(),
  parent_id uuid references public.messages(id) on delete cascade,
  device_id text not null check (char_length(device_id) between 8 and 200),
  user_id uuid references auth.users(id) on delete set null,
  author_name text not null default 'APH user' check (char_length(author_name) between 1 and 80),
  body text not null check (char_length(body) between 2 and 4000),
  message_type text not null default 'question' check (message_type in ('question', 'reply', 'announcement', 'support')),
  is_public boolean not null default true,
  status text not null default 'open' check (status in ('open', 'answered', 'closed', 'hidden')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.message_contacts (
  message_id uuid primary key references public.messages(id) on delete cascade,
  author_email text not null check (char_length(author_email) between 3 and 320),
  created_at timestamptz not null default now()
);

create index if not exists messages_created_idx on public.messages(created_at desc);
create index if not exists messages_parent_idx on public.messages(parent_id);
create index if not exists messages_device_idx on public.messages(device_id);

create trigger set_messages_updated_at
before update on public.messages
for each row execute function public.set_updated_at();

create table if not exists public.api_rate_limits (
  identifier_hash text not null,
  endpoint text not null,
  window_started_at timestamptz not null,
  request_count integer not null default 1,
  primary key (identifier_hash, endpoint, window_started_at)
);

create index if not exists api_rate_limits_window_idx on public.api_rate_limits(window_started_at);

create or replace function public.consume_api_quota(
  p_identifier_hash text,
  p_endpoint text,
  p_limit integer default 20,
  p_window_minutes integer default 60
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  bucket timestamptz;
  current_count integer;
begin
  bucket := to_timestamp(
    floor(extract(epoch from now()) / (greatest(p_window_minutes, 1) * 60))
    * (greatest(p_window_minutes, 1) * 60)
  );
  insert into public.api_rate_limits(identifier_hash, endpoint, window_started_at, request_count)
  values (p_identifier_hash, p_endpoint, bucket, 1)
  on conflict (identifier_hash, endpoint, window_started_at)
  do update set request_count = public.api_rate_limits.request_count + 1
  returning request_count into current_count;
  if random() < 0.01 then
    delete from public.api_rate_limits where window_started_at < now() - interval '7 days';
  end if;
  return current_count <= greatest(p_limit, 1);
end;
$$;

revoke all on function public.consume_api_quota(text, text, integer, integer) from public;
grant execute on function public.consume_api_quota(text, text, integer, integer) to service_role;

create or replace function public.submit_public_message(
  p_device_id text,
  p_author_name text,
  p_author_email text,
  p_body text
)
returns public.messages
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  submitted public.messages;
  clean_device text := left(trim(coalesce(p_device_id, '')), 200);
  clean_name text := left(trim(coalesce(p_author_name, '')), 80);
  clean_email text := lower(left(trim(coalesce(p_author_email, '')), 320));
  clean_body text := left(trim(coalesce(p_body, '')), 4000);
  quota_key text;
begin
  if char_length(clean_device) < 8 then
    raise exception 'A valid device identifier is required.' using errcode = '22023';
  end if;
  if char_length(clean_body) < 2 then
    raise exception 'Message must contain at least two characters.' using errcode = '22023';
  end if;
  if clean_email <> '' and clean_email !~* '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$' then
    raise exception 'Email address is invalid.' using errcode = '22023';
  end if;

  quota_key := encode(digest('message:' || clean_device, 'sha256'), 'hex');
  if not public.consume_api_quota(quota_key, 'community-message', 10, 60) then
    raise exception 'Message limit reached. Please try again later.' using errcode = 'P0001';
  end if;

  insert into public.messages (device_id, user_id, author_name, body, message_type, is_public)
  values (
    clean_device,
    auth.uid(),
    coalesce(nullif(clean_name, ''), 'APH user'),
    clean_body,
    'question',
    true
  )
  returning * into submitted;

  if clean_email <> '' then
    insert into public.message_contacts (message_id, author_email)
    values (submitted.id, clean_email);
  end if;
  return submitted;
end;
$$;

revoke all on function public.submit_public_message(text, text, text, text) from public;
grant execute on function public.submit_public_message(text, text, text, text) to anon, authenticated;

create table if not exists public.audit_log (
  id bigint generated always as identity primary key,
  table_name text not null,
  record_id text not null,
  operation text not null,
  old_data jsonb,
  new_data jsonb,
  actor_id uuid references auth.users(id),
  created_at timestamptz not null default now()
);

create or replace function public.log_catalog_change()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  -- Interactive administrator edits are audited in detail. Trusted bulk imports
  -- use import_jobs/checkpoints instead of duplicating millions of source rows.
  if auth.uid() is not null then
    insert into public.audit_log(table_name, record_id, operation, old_data, new_data, actor_id)
    values (tg_table_name, coalesce(new.id, old.id), tg_op, to_jsonb(old), to_jsonb(new), auth.uid());
  end if;
  return coalesce(new, old);
end;
$$;

create trigger audit_drugs
after insert or update or delete on public.drugs
for each row execute function public.log_catalog_change();

create trigger audit_products
after insert or update or delete on public.products
for each row execute function public.log_catalog_change();

create table if not exists public.catalog_tombstones (
  entity_type text not null check (entity_type in ('drug', 'product')),
  entity_id text not null,
  deleted_at timestamptz not null default now(),
  primary key (entity_type, entity_id)
);

create index if not exists catalog_tombstones_deleted_idx on public.catalog_tombstones(deleted_at desc);

create or replace function public.track_catalog_visibility()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  kind text := case when tg_table_name = 'drugs' then 'drug' else 'product' end;
begin
  if tg_op = 'DELETE' or (tg_op = 'UPDATE' and old.is_published and not new.is_published) then
    insert into public.catalog_tombstones(entity_type, entity_id, deleted_at)
    values (kind, old.id, now())
    on conflict (entity_type, entity_id) do update set deleted_at = excluded.deleted_at;
  elsif new.is_published then
    delete from public.catalog_tombstones where entity_type = kind and entity_id = new.id;
  end if;
  return coalesce(new, old);
end;
$$;

create trigger track_drug_visibility
after insert or update or delete on public.drugs
for each row execute function public.track_catalog_visibility();

create trigger track_product_visibility
after insert or update or delete on public.products
for each row execute function public.track_catalog_visibility();

create or replace function public.search_medicines(search_query text, max_results integer default 50)
returns table (
  drug jsonb,
  matched_product jsonb,
  score real,
  matched_on text
)
language sql
stable
security invoker
set search_path = public, extensions
as $$
  with q as (
    select
      lower(trim(search_query)) as term,
      websearch_to_tsquery('simple', trim(search_query)) as tsquery
  ),
  drug_hits as (
    select
      d.id,
      to_jsonb(d) - 'search_document' as drug,
      null::jsonb as matched_product,
      greatest(
        similarity(lower(d.generic_name), q.term),
        similarity(lower(d.arabic_name), q.term),
        coalesce((select max(similarity(lower(alias), q.term)) from unnest(d.aliases) alias), 0),
        ts_rank(d.search_document, q.tsquery)
      )::real as score,
      'drug'::text as matched_on
    from public.drugs d cross join q
    where (d.is_published or public.is_admin())
      and (
        lower(d.generic_name) % q.term or lower(d.arabic_name) % q.term
        or q.term = any(select lower(alias) from unnest(d.aliases) alias)
        or d.search_document @@ q.tsquery
        or lower(d.generic_name) like '%' || q.term || '%'
        or lower(d.arabic_name) like '%' || q.term || '%'
      )
  ),
  product_hits as (
    select
      d.id,
      to_jsonb(d) - 'search_document' as drug,
      to_jsonb(p) - 'search_document' as matched_product,
      greatest(
        similarity(lower(p.brand_name), q.term),
        similarity(lower(p.brand_name_ar), q.term),
        similarity(lower(p.generic_name), q.term),
        ts_rank(p.search_document, q.tsquery),
        case when p.barcode = search_query then 1 else 0 end
      )::real as score,
      'product'::text as matched_on
    from public.products p
    join public.drugs d on d.id = p.drug_id
    cross join q
    where (p.is_published or public.is_admin()) and (d.is_published or public.is_admin())
      and (
        lower(p.brand_name) % q.term or lower(p.brand_name_ar) % q.term
        or lower(p.generic_name) % q.term or p.barcode = search_query
        or p.search_document @@ q.tsquery
        or lower(p.brand_name) like '%' || q.term || '%'
      )
  ),
  combined as (
    select * from drug_hits
    union all
    select * from product_hits
  ),
  ranked as (
    select distinct on (id) drug, matched_product, score, matched_on
    from combined
    order by id, score desc
  )
  select drug, matched_product, score, matched_on
  from ranked
  order by score desc
  limit least(greatest(max_results, 1), 200);
$$;

grant execute on function public.search_medicines(text, integer) to anon, authenticated;

create or replace function public.admin_unpublish_drug(p_drug_id text)
returns void
language plpgsql
security invoker
set search_path = public
as $$
begin
  if not public.is_admin() then
    raise exception 'Administrator access required.' using errcode = '42501';
  end if;
  update public.products set is_published = false where drug_id = p_drug_id and is_published;
  update public.drugs set is_published = false where id = p_drug_id and is_published;
end;
$$;

revoke all on function public.admin_unpublish_drug(text) from public;
grant execute on function public.admin_unpublish_drug(text) to authenticated;

alter table public.body_systems enable row level security;
alter table public.profiles enable row level security;
alter table public.drugs enable row level security;
alter table public.products enable row level security;
alter table public.import_jobs enable row level security;
alter table public.messages enable row level security;
alter table public.message_contacts enable row level security;
alter table public.api_rate_limits enable row level security;
alter table public.audit_log enable row level security;
alter table public.catalog_tombstones enable row level security;

create policy "Body systems are public" on public.body_systems for select using (true);
create policy "Published drugs are public" on public.drugs for select using (is_published or public.is_admin());
create policy "Admins manage drugs" on public.drugs for all using (public.is_admin()) with check (public.is_admin());
create policy "Published products are public" on public.products for select using (is_published or public.is_admin());
create policy "Admins manage products" on public.products for all using (public.is_admin()) with check (public.is_admin());
create policy "Users read own profile" on public.profiles for select using (id = auth.uid() or public.is_admin());
create policy "Users update own profile" on public.profiles for update using (id = auth.uid()) with check (id = auth.uid());
create policy "Admins manage profiles" on public.profiles for all using (public.is_admin()) with check (public.is_admin());
create policy "Admins manage imports" on public.import_jobs for all using (public.is_admin()) with check (public.is_admin());
create policy "Public messages are readable" on public.messages for select using ((is_public and status <> 'hidden') or public.is_admin() or user_id = auth.uid());
create policy "Admins manage messages" on public.messages for all using (public.is_admin()) with check (public.is_admin());
create policy "Admins read message contacts" on public.message_contacts for select using (public.is_admin());
create policy "Admins manage message contacts" on public.message_contacts for all using (public.is_admin()) with check (public.is_admin());
create policy "Admins read audit log" on public.audit_log for select using (public.is_admin());
create policy "Catalog removals are public" on public.catalog_tombstones for select using (true);
create policy "Admins manage catalog removals" on public.catalog_tombstones for all using (public.is_admin()) with check (public.is_admin());

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('medicine-images', 'medicine-images', true, 8388608, array['image/jpeg', 'image/png', 'image/webp']),
  ('database-imports', 'database-imports', false, 1073741824, array['text/csv', 'application/json', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'])
on conflict (id) do update set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

create policy "Medicine images are public" on storage.objects for select using (bucket_id = 'medicine-images');
create policy "Admins upload medicine images" on storage.objects for insert to authenticated with check (bucket_id = 'medicine-images' and public.is_admin());
create policy "Admins update medicine images" on storage.objects for update to authenticated using (bucket_id = 'medicine-images' and public.is_admin()) with check (bucket_id = 'medicine-images' and public.is_admin());
create policy "Admins delete medicine images" on storage.objects for delete to authenticated using (bucket_id = 'medicine-images' and public.is_admin());
create policy "Admins manage database imports" on storage.objects for all to authenticated using (bucket_id = 'database-imports' and public.is_admin()) with check (bucket_id = 'database-imports' and public.is_admin());

do $$
begin
  alter publication supabase_realtime add table public.drugs;
exception when duplicate_object then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.products;
exception when duplicate_object then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.messages;
exception when duplicate_object then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.catalog_tombstones;
exception when duplicate_object then null;
end $$;
