-- APH V2 · normalized pharmaceutical domain, review workflow, provenance,
-- reliable sync feed, private support, notifications, and resumable imports.
-- This migration extends the V1 mobile/admin schema without breaking its API.

-- ---------------------------------------------------------------------------
-- Roles and authorization helpers
-- ---------------------------------------------------------------------------

alter table public.profiles drop constraint if exists profiles_role_check;
update public.profiles set role = 'pharmacist_editor' where role in ('editor', 'pharmacist');
alter table public.profiles
  add constraint profiles_role_check
  check (role in ('owner', 'admin', 'pharmacist_editor', 'reviewer', 'support', 'user'));

create or replace function public.has_any_role(allowed_roles text[])
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.profiles
    where id = auth.uid() and role = any(allowed_roles)
  );
$$;

create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public.has_any_role(array['owner', 'admin']);
$$;

create or replace function public.can_edit_catalog()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public.has_any_role(array['owner', 'admin', 'pharmacist_editor']);
$$;

create or replace function public.can_review_catalog()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public.has_any_role(array['owner', 'admin', 'reviewer']);
$$;

create or replace function public.can_support_users()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public.has_any_role(array['owner', 'admin', 'support']);
$$;

-- Publishing and unpublishing remain owner/admin actions; pharmacist editors
-- submit drafts for reviewer approval instead of bypassing the workflow.
create or replace function public.admin_unpublish_drug(p_drug_id text)
returns void
language plpgsql
security invoker
set search_path = public
as $$
begin
  if not public.is_admin() then
    raise exception 'Owner or administrator access required.' using errcode = '42501';
  end if;
  update public.products set is_published = false where drug_id = p_drug_id and is_published;
  update public.drugs set is_published = false where id = p_drug_id and is_published;
end;
$$;

-- ---------------------------------------------------------------------------
-- Normalized reference entities
-- ---------------------------------------------------------------------------

create table public.countries (
  id uuid primary key default gen_random_uuid(),
  iso2 text unique check (iso2 is null or iso2 ~ '^[A-Z]{2}$'),
  iso3 text unique check (iso3 is null or iso3 ~ '^[A-Z]{3}$'),
  name_en text not null,
  name_ar text not null default '',
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.atc_classifications (
  code text primary key,
  parent_code text references public.atc_classifications(code),
  level smallint not null check (level between 1 and 5),
  name_en text not null,
  name_ar text not null default '',
  source_url text,
  source_version text,
  last_verified_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.manufacturers (
  id uuid primary key default gen_random_uuid(),
  legal_name text not null,
  display_name text not null,
  display_name_ar text not null default '',
  country_id uuid references public.countries(id),
  website text,
  verification_status text not null default 'unverified'
    check (verification_status in ('unverified', 'pending', 'verified', 'rejected')),
  source_note text,
  created_by uuid references auth.users(id),
  updated_by uuid references auth.users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index manufacturers_name_unique on public.manufacturers(lower(legal_name));

create table public.market_partners (
  id uuid primary key default gen_random_uuid(),
  partner_type text not null check (partner_type in ('agent', 'supplier', 'distributor')),
  legal_name text not null,
  display_name_ar text not null default '',
  country_id uuid references public.countries(id),
  governorate text,
  contact_phone text,
  contact_email text,
  website text,
  verification_status text not null default 'unverified'
    check (verification_status in ('unverified', 'pending', 'verified', 'rejected')),
  last_verified_at timestamptz,
  created_by uuid references auth.users(id),
  updated_by uuid references auth.users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index market_partners_type_idx on public.market_partners(partner_type);
create index market_partners_name_trgm_idx on public.market_partners using gin (legal_name gin_trgm_ops);

create table public.dosage_forms (
  id uuid primary key default gen_random_uuid(),
  code text unique,
  name_en text not null,
  name_ar text not null default '',
  route text not null default '',
  route_ar text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index dosage_forms_name_route_unique on public.dosage_forms(lower(name_en), lower(route));

-- ---------------------------------------------------------------------------
-- Rich active-ingredient and branded-product records
-- ---------------------------------------------------------------------------

alter table public.drugs
  add column if not exists atc_code text references public.atc_classifications(code),
  add column if not exists therapeutic_class text not null default '',
  add column if not exists therapeutic_class_ar text not null default '',
  add column if not exists contraindications text[] not null default '{}',
  add column if not exists contraindications_ar text[] not null default '{}',
  add column if not exists adverse_effects text[] not null default '{}',
  add column if not exists adverse_effects_ar text[] not null default '{}',
  add column if not exists food_interactions text[] not null default '{}',
  add column if not exists food_interactions_ar text[] not null default '{}',
  add column if not exists renal_adjustment text not null default '',
  add column if not exists renal_adjustment_ar text not null default '',
  add column if not exists hepatic_adjustment text not null default '',
  add column if not exists hepatic_adjustment_ar text not null default '',
  add column if not exists pregnancy_lactation text not null default '',
  add column if not exists pregnancy_lactation_ar text not null default '',
  add column if not exists overdose text not null default '',
  add column if not exists overdose_ar text not null default '',
  add column if not exists evidence_level text not null default 'unrated'
    check (evidence_level in ('unrated', 'low', 'moderate', 'high', 'regulatory')),
  add column if not exists review_status text not null default 'draft'
    check (review_status in ('draft', 'pending_review', 'approved', 'rejected', 'archived')),
  add column if not exists approved_by uuid references auth.users(id),
  add column if not exists approved_at timestamptz,
  add column if not exists last_verified_at timestamptz;

update public.drugs
set review_status = 'approved', approved_at = coalesce(approved_at, updated_at)
where is_published and review_status = 'draft';

create index if not exists drugs_atc_idx on public.drugs(atc_code);
create index if not exists drugs_review_status_idx on public.drugs(review_status, updated_at desc);

alter table public.products
  add column if not exists pack_size text not null default '',
  add column if not exists route text not null default '',
  add column if not exists route_ar text not null default '',
  add column if not exists manufacturer_id uuid references public.manufacturers(id),
  add column if not exists manufacturing_country_id uuid references public.countries(id),
  add column if not exists dosage_form_id uuid references public.dosage_forms(id),
  add column if not exists availability_status text not null default 'unknown'
    check (availability_status in ('unknown', 'available', 'limited', 'unavailable', 'discontinued', 'unconfirmed')),
  add column if not exists source_note text not null default '',
  add column if not exists pharmacist_notes text not null default '',
  add column if not exists review_status text not null default 'draft'
    check (review_status in ('draft', 'pending_review', 'approved', 'rejected', 'archived')),
  add column if not exists approved_by uuid references auth.users(id),
  add column if not exists approved_at timestamptz,
  add column if not exists last_verified_at timestamptz;

update public.products
set review_status = 'approved', approved_at = coalesce(approved_at, updated_at)
where is_published and review_status = 'draft';

create index if not exists products_review_status_idx on public.products(review_status, updated_at desc);
create index if not exists products_manufacturer_id_idx on public.products(manufacturer_id);

-- Supports combination medicines without breaking V1's primary drug_id field.
create table public.product_active_ingredients (
  product_id text not null references public.products(id) on delete cascade,
  ingredient_id text not null references public.drugs(id) on delete cascade,
  component_strength text not null default '',
  is_primary boolean not null default false,
  display_order smallint not null default 1,
  created_at timestamptz not null default now(),
  primary key (product_id, ingredient_id)
);

insert into public.product_active_ingredients(product_id, ingredient_id, component_strength, is_primary)
select id, drug_id, strength, true from public.products
on conflict (product_id, ingredient_id) do update set
  component_strength = excluded.component_strength,
  is_primary = true;

-- Preserve compatibility with V1 writers: their required products.drug_id is
-- the primary component. Combination components are added explicitly alongside it.
create or replace function public.sync_primary_product_ingredient()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  delete from public.product_active_ingredients
  where product_id = new.id
    and is_primary
    and ingredient_id <> new.drug_id;

  insert into public.product_active_ingredients(
    product_id, ingredient_id, component_strength, is_primary, display_order
  ) values (new.id, new.drug_id, new.strength, true, 1)
  on conflict (product_id, ingredient_id) do update set
    component_strength = excluded.component_strength,
    is_primary = true,
    display_order = 1;
  return new;
end;
$$;

create trigger sync_primary_product_ingredient
  after insert or update of drug_id, strength on public.products
  for each row execute function public.sync_primary_product_ingredient();

create table public.product_market_records (
  id uuid primary key default gen_random_uuid(),
  product_id text not null references public.products(id) on delete cascade,
  country_id uuid references public.countries(id),
  market_code text not null default 'YE',
  agent_id uuid references public.market_partners(id),
  supplier_id uuid references public.market_partners(id),
  governorate text,
  registration_number text,
  registration_status text not null default 'unconfirmed'
    check (registration_status in ('unconfirmed', 'pending', 'registered', 'suspended', 'expired', 'cancelled')),
  availability_status text not null default 'unconfirmed'
    check (availability_status in ('unconfirmed', 'available', 'limited', 'unavailable', 'discontinued')),
  verification_status text not null default 'unverified'
    check (verification_status in ('unverified', 'pending', 'verified', 'rejected')),
  source_url text,
  source_document_path text,
  verification_notes text,
  last_verified_at timestamptz,
  verified_by uuid references auth.users(id),
  created_by uuid references auth.users(id),
  updated_by uuid references auth.users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (product_id, market_code, registration_number)
);
create index product_market_product_idx on public.product_market_records(product_id);
create index product_market_status_idx on public.product_market_records(market_code, availability_status);

-- ---------------------------------------------------------------------------
-- Sources, field-level provenance, interactions, and safe alternatives
-- ---------------------------------------------------------------------------

create table public.sources (
  id uuid primary key default gen_random_uuid(),
  source_type text not null
    check (source_type in ('who_atc', 'rxnorm', 'openfda', 'dailymed', 'ema', 'mhra', 'manufacturer', 'regulator', 'ministry', 'licensed_database', 'pharmacist', 'other')),
  authority text not null,
  title text not null,
  url text,
  document_storage_path text,
  published_on date,
  retrieved_at timestamptz not null default now(),
  source_version text,
  license_note text,
  checksum_sha256 text,
  verification_status text not null default 'pending'
    check (verification_status in ('pending', 'verified', 'rejected', 'expired')),
  created_by uuid references auth.users(id),
  verified_by uuid references auth.users(id),
  verified_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (url is not null or document_storage_path is not null)
);
create index sources_authority_idx on public.sources(authority);
create index sources_type_status_idx on public.sources(source_type, verification_status);

create table public.field_provenance (
  id bigint generated always as identity primary key,
  entity_type text not null
    check (entity_type in ('drug', 'product', 'manufacturer', 'market_partner', 'market_record', 'interaction', 'alternative')),
  entity_id text not null,
  field_name text not null,
  source_id uuid not null references public.sources(id) on delete cascade,
  value_hash text,
  source_excerpt text,
  is_public boolean not null default true,
  verified_by uuid references auth.users(id),
  verified_at timestamptz,
  created_at timestamptz not null default now(),
  unique (entity_type, entity_id, field_name, source_id)
);
create index field_provenance_entity_idx on public.field_provenance(entity_type, entity_id);

create table public.drug_interactions (
  id uuid primary key default gen_random_uuid(),
  ingredient_id text not null references public.drugs(id) on delete cascade,
  interacting_ingredient_id text references public.drugs(id) on delete cascade,
  interacting_substance text not null default '',
  interaction_type text not null default 'drug'
    check (interaction_type in ('drug', 'food', 'laboratory', 'disease', 'other')),
  severity text not null default 'unknown'
    check (severity in ('unknown', 'minor', 'moderate', 'major', 'contraindicated')),
  effect_en text not null,
  effect_ar text not null default '',
  management_en text not null default '',
  management_ar text not null default '',
  evidence_level text not null default 'unrated',
  review_status text not null default 'draft'
    check (review_status in ('draft', 'pending_review', 'approved', 'rejected', 'archived')),
  created_by uuid references auth.users(id),
  approved_by uuid references auth.users(id),
  approved_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (interacting_ingredient_id is not null or interacting_substance <> '')
);
create index drug_interactions_ingredient_idx on public.drug_interactions(ingredient_id, severity);

create table public.product_alternatives (
  product_id text not null references public.products(id) on delete cascade,
  alternative_product_id text not null references public.products(id) on delete cascade,
  same_ingredients boolean not null,
  same_strength boolean not null,
  same_dosage_form boolean not null,
  same_route boolean not null,
  confidence numeric(5,4) not null check (confidence between 0 and 1),
  explanation text not null default '',
  review_status text not null default 'pending_review'
    check (review_status in ('draft', 'pending_review', 'approved', 'rejected', 'archived')),
  reviewed_by uuid references auth.users(id),
  reviewed_at timestamptz,
  created_at timestamptz not null default now(),
  primary key (product_id, alternative_product_id),
  check (product_id <> alternative_product_id)
);
create index product_alternatives_reverse_idx on public.product_alternatives(alternative_product_id);

-- ---------------------------------------------------------------------------
-- Review/version history and reliable sequence-based synchronization
-- ---------------------------------------------------------------------------

create table public.catalog_revisions (
  id bigint generated always as identity primary key,
  entity_type text not null check (entity_type in ('drug', 'product', 'manufacturer', 'market_partner', 'market_record', 'source', 'interaction')),
  entity_id text not null,
  entity_version bigint,
  change_type text not null check (change_type in ('create', 'update', 'publish', 'unpublish', 'archive', 'delete', 'restore')),
  old_data jsonb,
  new_data jsonb,
  approval_status text not null default 'pending_review'
    check (approval_status in ('draft', 'pending_review', 'approved', 'rejected')),
  change_reason text,
  changed_by uuid references auth.users(id),
  reviewed_by uuid references auth.users(id),
  reviewed_at timestamptz,
  review_note text,
  created_at timestamptz not null default now()
);
create index catalog_revisions_entity_idx on public.catalog_revisions(entity_type, entity_id, created_at desc);
create index catalog_revisions_queue_idx on public.catalog_revisions(approval_status, created_at);

create table public.catalog_change_feed (
  sequence bigint generated always as identity primary key,
  entity_type text not null check (entity_type in ('drug', 'product')),
  entity_id text not null,
  operation text not null check (operation in ('UPSERT', 'DELETE')),
  entity_version bigint,
  is_public boolean not null default false,
  changed_at timestamptz not null default now()
);
create index catalog_change_feed_cursor_idx on public.catalog_change_feed(sequence, is_public);
create index catalog_change_feed_entity_idx on public.catalog_change_feed(entity_type, entity_id, sequence desc);

create or replace function public.append_catalog_change_feed()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  kind text := case when tg_table_name = 'drugs' then 'drug' else 'product' end;
  became_hidden boolean := tg_op = 'UPDATE' and old.is_published and not new.is_published;
begin
  if tg_op = 'DELETE' then
    insert into public.catalog_change_feed(entity_type, entity_id, operation, entity_version, is_public)
    values (kind, old.id, 'DELETE', old.version, old.is_published);
  elsif became_hidden then
    insert into public.catalog_change_feed(entity_type, entity_id, operation, entity_version, is_public)
    values (kind, new.id, 'DELETE', new.version, true);
  else
    insert into public.catalog_change_feed(entity_type, entity_id, operation, entity_version, is_public)
    values (kind, new.id, 'UPSERT', new.version, new.is_published);
  end if;
  return coalesce(new, old);
end;
$$;

create trigger append_drug_change_feed
after insert or update or delete on public.drugs
for each row execute function public.append_catalog_change_feed();

create trigger append_product_change_feed
after insert or update or delete on public.products
for each row execute function public.append_catalog_change_feed();

create table public.sync_devices (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users(id) on delete cascade,
  device_identifier_hash text not null unique,
  platform text not null default 'unknown',
  app_version text,
  locale text,
  last_sequence bigint not null default 0,
  last_synced_at timestamptz,
  push_enabled boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create or replace function public.submit_catalog_for_review(
  p_entity_type text,
  p_entity_id text,
  p_reason text default null
)
returns bigint
language plpgsql
security definer
set search_path = public
as $$
declare
  snapshot jsonb;
  record_version bigint;
  revision_id bigint;
begin
  if not public.can_edit_catalog() then
    raise exception 'Catalog editor access required.' using errcode = '42501';
  end if;

  if p_entity_type = 'drug' then
    update public.drugs set
      review_status = 'pending_review', is_published = false, updated_by = auth.uid()
    where id = p_entity_id and (public.is_admin() or not is_published)
    returning to_jsonb(drugs.*), version into snapshot, record_version;
  elsif p_entity_type = 'product' then
    update public.products set
      review_status = 'pending_review', is_published = false, updated_by = auth.uid()
    where id = p_entity_id and (public.is_admin() or not is_published)
    returning to_jsonb(products.*), version into snapshot, record_version;
  else
    raise exception 'Unsupported catalog entity.' using errcode = '22023';
  end if;

  if snapshot is null then
    raise exception 'Catalog record not found.' using errcode = 'P0002';
  end if;

  insert into public.catalog_revisions(
    entity_type, entity_id, entity_version, change_type, new_data,
    approval_status, change_reason, changed_by
  ) values (
    p_entity_type, p_entity_id, record_version, 'update', snapshot,
    'pending_review', left(coalesce(p_reason, ''), 2000), auth.uid()
  ) returning id into revision_id;
  return revision_id;
end;
$$;

revoke all on function public.submit_catalog_for_review(text, text, text) from public;
grant execute on function public.submit_catalog_for_review(text, text, text) to authenticated;

create or replace function public.review_catalog_record(
  p_entity_type text,
  p_entity_id text,
  p_decision text,
  p_note text default null
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  next_status text;
  affected_rows integer := 0;
begin
  if not public.can_review_catalog() then
    raise exception 'Reviewer access required.' using errcode = '42501';
  end if;
  if p_decision not in ('approved', 'rejected') then
    raise exception 'Decision must be approved or rejected.' using errcode = '22023';
  end if;
  next_status := p_decision;

  if p_entity_type = 'drug' then
    update public.drugs set
      review_status = next_status,
      is_published = (p_decision = 'approved'),
      approved_by = case when p_decision = 'approved' then auth.uid() else null end,
      approved_at = case when p_decision = 'approved' then now() else null end,
      updated_by = auth.uid()
    where id = p_entity_id;
    get diagnostics affected_rows = row_count;
  elsif p_entity_type = 'product' then
    if p_decision = 'approved' and not exists (
      select 1 from public.products p
      join public.drugs d on d.id = p.drug_id
      where p.id = p_entity_id and d.is_published and d.review_status = 'approved'
    ) then
      raise exception 'The primary ingredient must be approved before this product.' using errcode = '23514';
    end if;
    update public.products set
      review_status = next_status,
      is_published = (p_decision = 'approved'),
      approved_by = case when p_decision = 'approved' then auth.uid() else null end,
      approved_at = case when p_decision = 'approved' then now() else null end,
      updated_by = auth.uid()
    where id = p_entity_id;
    get diagnostics affected_rows = row_count;
  else
    raise exception 'Unsupported catalog entity.' using errcode = '22023';
  end if;

  if affected_rows = 0 then
    raise exception 'Catalog record not found.' using errcode = 'P0002';
  end if;

  update public.catalog_revisions set
    approval_status = p_decision,
    reviewed_by = auth.uid(),
    reviewed_at = now(),
    review_note = left(coalesce(p_note, ''), 2000)
  where id = (
    select id from public.catalog_revisions
    where entity_type = p_entity_type and entity_id = p_entity_id
      and approval_status = 'pending_review'
    order by created_at desc limit 1
  );
end;
$$;

revoke all on function public.review_catalog_record(text, text, text, text) from public;
grant execute on function public.review_catalog_record(text, text, text, text) to authenticated;

-- ---------------------------------------------------------------------------
-- Resumable imports, validation reports, rollback metadata, and ZIP assets
-- ---------------------------------------------------------------------------

alter table public.import_jobs
  add column if not exists source_sha256 text,
  add column if not exists source_size bigint,
  add column if not exists mapping jsonb not null default '{}'::jsonb,
  add column if not exists checkpoint jsonb not null default '{}'::jsonb,
  add column if not exists validation_summary jsonb not null default '{}'::jsonb,
  add column if not exists rollback_enabled boolean not null default false,
  add column if not exists rolled_back_at timestamptz,
  add column if not exists rolled_back_by uuid references auth.users(id),
  add column if not exists parent_job_id uuid references public.import_jobs(id),
  add column if not exists updated_at timestamptz not null default now();

create table public.import_batches (
  id bigint generated always as identity primary key,
  import_job_id uuid not null references public.import_jobs(id) on delete cascade,
  batch_number integer not null,
  first_row bigint not null,
  last_row bigint not null,
  imported_rows integer not null default 0,
  skipped_rows integer not null default 0,
  image_rows integer not null default 0,
  status text not null default 'processing'
    check (status in ('processing', 'complete', 'partial', 'failed', 'rolled_back')),
  started_at timestamptz not null default now(),
  completed_at timestamptz,
  unique (import_job_id, batch_number)
);

create table public.import_errors (
  id bigint generated always as identity primary key,
  import_job_id uuid not null references public.import_jobs(id) on delete cascade,
  batch_id bigint references public.import_batches(id) on delete cascade,
  row_number bigint,
  column_name text,
  error_code text not null,
  error_message text not null,
  raw_row jsonb,
  created_at timestamptz not null default now()
);
create index import_errors_job_idx on public.import_errors(import_job_id, row_number);

create table public.import_row_changes (
  id bigint generated always as identity primary key,
  import_job_id uuid not null references public.import_jobs(id) on delete cascade,
  entity_type text not null check (entity_type in ('drug', 'product', 'manufacturer', 'market_partner', 'market_record')),
  entity_id text not null,
  operation text not null check (operation in ('insert', 'update')),
  old_data jsonb,
  new_version bigint,
  created_at timestamptz not null default now()
);
create index import_row_changes_job_idx on public.import_row_changes(import_job_id, id);

-- ---------------------------------------------------------------------------
-- Private support conversations, attachments, announcements, and reports
-- ---------------------------------------------------------------------------

create table public.conversations (
  id uuid primary key default gen_random_uuid(),
  subject text not null default 'APH support',
  created_by uuid not null references auth.users(id) on delete cascade,
  assigned_to uuid references auth.users(id) on delete set null,
  status text not null default 'open' check (status in ('open', 'pending', 'answered', 'closed', 'archived')),
  priority text not null default 'normal' check (priority in ('low', 'normal', 'high', 'urgent')),
  contains_sensitive_data boolean not null default false,
  last_message_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.conversation_members (
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  member_role text not null default 'participant' check (member_role in ('participant', 'support', 'owner')),
  last_read_at timestamptz,
  joined_at timestamptz not null default now(),
  primary key (conversation_id, user_id)
);

create table public.conversation_messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  sender_id uuid not null references auth.users(id) on delete cascade,
  body text not null check (char_length(body) between 1 and 6000),
  message_type text not null default 'text' check (message_type in ('text', 'system', 'image', 'file')),
  moderation_status text not null default 'visible' check (moderation_status in ('visible', 'hidden', 'flagged')),
  created_at timestamptz not null default now(),
  edited_at timestamptz
);
create index conversation_messages_thread_idx on public.conversation_messages(conversation_id, created_at);

create table public.message_attachments (
  id uuid primary key default gen_random_uuid(),
  message_id uuid not null references public.conversation_messages(id) on delete cascade,
  storage_path text not null unique,
  original_name text not null,
  mime_type text not null,
  file_size bigint not null check (file_size between 1 and 10485760),
  checksum_sha256 text,
  scan_status text not null default 'pending' check (scan_status in ('pending', 'clean', 'blocked', 'failed')),
  uploaded_by uuid not null default auth.uid() references auth.users(id),
  created_at timestamptz not null default now()
);

create table public.announcements (
  id uuid primary key default gen_random_uuid(),
  title_en text not null,
  title_ar text not null default '',
  body_en text not null,
  body_ar text not null default '',
  severity text not null default 'info' check (severity in ('info', 'success', 'warning', 'critical')),
  audience text not null default 'all' check (audience in ('all', 'pharmacists', 'editors', 'admins')),
  is_published boolean not null default false,
  starts_at timestamptz,
  ends_at timestamptz,
  created_by uuid references auth.users(id),
  published_by uuid references auth.users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  notification_type text not null,
  title text not null,
  body text not null,
  action_url text,
  data jsonb not null default '{}'::jsonb,
  read_at timestamptz,
  created_at timestamptz not null default now()
);
create index notifications_user_idx on public.notifications(user_id, created_at desc);

create table public.push_devices (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  platform text not null check (platform in ('android', 'ios', 'web')),
  token_ciphertext text not null,
  token_hash text not null unique,
  enabled boolean not null default true,
  last_seen_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);

create table public.data_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid references auth.users(id) on delete set null,
  entity_type text not null check (entity_type in ('drug', 'product', 'interaction', 'market_record', 'other')),
  entity_id text,
  report_type text not null check (report_type in ('incorrect', 'outdated', 'missing', 'safety', 'image', 'other')),
  description text not null check (char_length(description) between 10 and 4000),
  status text not null default 'open' check (status in ('open', 'triaged', 'resolved', 'rejected', 'closed')),
  assigned_to uuid references auth.users(id),
  resolution_note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- RLS-safe private-conversation helpers avoid recursive policy evaluation
-- between conversations, membership, messages, and attachments.
create or replace function public.can_access_conversation(p_conversation_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public.can_support_users() or exists (
    select 1 from public.conversations c
    where c.id = p_conversation_id
      and (c.created_by = auth.uid() or c.assigned_to = auth.uid())
  ) or exists (
    select 1 from public.conversation_members cm
    where cm.conversation_id = p_conversation_id and cm.user_id = auth.uid()
  );
$$;

create or replace function public.can_access_message(p_message_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.conversation_messages m
    where m.id = p_message_id and public.can_access_conversation(m.conversation_id)
  );
$$;

revoke all on function public.can_access_conversation(uuid) from public;
revoke all on function public.can_access_message(uuid) from public;
grant execute on function public.can_access_conversation(uuid) to authenticated;
grant execute on function public.can_access_message(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- Updated timestamps
-- ---------------------------------------------------------------------------

create trigger set_countries_updated_at before update on public.countries
for each row execute function public.set_updated_at();
create trigger set_atc_updated_at before update on public.atc_classifications
for each row execute function public.set_updated_at();
create trigger set_manufacturers_updated_at before update on public.manufacturers
for each row execute function public.set_updated_at();
create trigger set_market_partners_updated_at before update on public.market_partners
for each row execute function public.set_updated_at();
create trigger set_dosage_forms_updated_at before update on public.dosage_forms
for each row execute function public.set_updated_at();
create trigger set_product_market_updated_at before update on public.product_market_records
for each row execute function public.set_updated_at();
create trigger set_sources_updated_at before update on public.sources
for each row execute function public.set_updated_at();
create trigger set_interactions_updated_at before update on public.drug_interactions
for each row execute function public.set_updated_at();
create trigger set_import_jobs_v2_updated_at before update on public.import_jobs
for each row execute function public.set_updated_at();
create trigger set_conversations_updated_at before update on public.conversations
for each row execute function public.set_updated_at();
create trigger set_announcements_updated_at before update on public.announcements
for each row execute function public.set_updated_at();
create trigger set_reports_updated_at before update on public.data_reports
for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------
-- Row-level security
-- ---------------------------------------------------------------------------

alter table public.countries enable row level security;
alter table public.atc_classifications enable row level security;
alter table public.manufacturers enable row level security;
alter table public.market_partners enable row level security;
alter table public.dosage_forms enable row level security;
alter table public.product_active_ingredients enable row level security;
alter table public.product_market_records enable row level security;
alter table public.sources enable row level security;
alter table public.field_provenance enable row level security;
alter table public.drug_interactions enable row level security;
alter table public.product_alternatives enable row level security;
alter table public.catalog_revisions enable row level security;
alter table public.catalog_change_feed enable row level security;
alter table public.sync_devices enable row level security;
alter table public.import_batches enable row level security;
alter table public.import_errors enable row level security;
alter table public.import_row_changes enable row level security;
alter table public.conversations enable row level security;
alter table public.conversation_members enable row level security;
alter table public.conversation_messages enable row level security;
alter table public.message_attachments enable row level security;
alter table public.announcements enable row level security;
alter table public.notifications enable row level security;
alter table public.push_devices enable row level security;
alter table public.data_reports enable row level security;

-- Replace V1 policies with the expanded role model.
drop policy if exists "Published drugs are public" on public.drugs;
drop policy if exists "Admins manage drugs" on public.drugs;
drop policy if exists "Published products are public" on public.products;
drop policy if exists "Admins manage products" on public.products;
drop policy if exists "Admins manage profiles" on public.profiles;
drop policy if exists "Admins manage imports" on public.import_jobs;
drop policy if exists "Admins manage messages" on public.messages;
drop policy if exists "Admins read audit log" on public.audit_log;
drop policy if exists "Admins manage catalog removals" on public.catalog_tombstones;

create policy "Published or staff drugs" on public.drugs for select
using (is_published or public.can_edit_catalog() or public.can_review_catalog());
create policy "Owners and admins manage drugs" on public.drugs for all
using (public.is_admin()) with check (public.is_admin());
create policy "Pharmacist editors create drug drafts" on public.drugs for insert
with check (
  public.has_any_role(array['pharmacist_editor'])
  and not is_published
  and review_status in ('draft', 'pending_review', 'rejected')
);
create policy "Pharmacist editors update drug drafts" on public.drugs for update
using (public.has_any_role(array['pharmacist_editor']) and not is_published)
with check (
  public.has_any_role(array['pharmacist_editor'])
  and not is_published
  and review_status in ('draft', 'pending_review', 'rejected')
);
create policy "Pharmacist editors delete drug drafts" on public.drugs for delete
using (public.has_any_role(array['pharmacist_editor']) and not is_published);

create policy "Published or staff products" on public.products for select
using (is_published or public.can_edit_catalog() or public.can_review_catalog());
create policy "Owners and admins manage products" on public.products for all
using (public.is_admin()) with check (public.is_admin());
create policy "Pharmacist editors create product drafts" on public.products for insert
with check (
  public.has_any_role(array['pharmacist_editor'])
  and not is_published
  and review_status in ('draft', 'pending_review', 'rejected')
);
create policy "Pharmacist editors update product drafts" on public.products for update
using (public.has_any_role(array['pharmacist_editor']) and not is_published)
with check (
  public.has_any_role(array['pharmacist_editor'])
  and not is_published
  and review_status in ('draft', 'pending_review', 'rejected')
);
create policy "Pharmacist editors delete product drafts" on public.products for delete
using (public.has_any_role(array['pharmacist_editor']) and not is_published);
create policy "Owners and admins manage profiles" on public.profiles for all
using (public.is_admin()) with check (public.is_admin());
create policy "Catalog editors manage imports" on public.import_jobs for all
using (public.can_edit_catalog()) with check (public.can_edit_catalog());
create policy "Support staff manage public messages" on public.messages for all
using (public.can_support_users()) with check (public.can_support_users());
create policy "Authorized staff read audit log" on public.audit_log for select
using (public.is_admin() or public.can_review_catalog());
create policy "Catalog editors manage removals" on public.catalog_tombstones for all
using (public.can_edit_catalog()) with check (public.can_edit_catalog());

create policy "Reference countries are public" on public.countries for select using (is_active);
create policy "Editors manage countries" on public.countries for all using (public.can_edit_catalog()) with check (public.can_edit_catalog());
create policy "ATC is public" on public.atc_classifications for select using (true);
create policy "Editors manage ATC" on public.atc_classifications for all using (public.can_edit_catalog()) with check (public.can_edit_catalog());
create policy "Manufacturers are public" on public.manufacturers for select using (verification_status = 'verified' or public.can_edit_catalog() or public.can_review_catalog());
create policy "Editors manage manufacturers" on public.manufacturers for all using (public.can_edit_catalog()) with check (public.can_edit_catalog());
create policy "Verified partners are public" on public.market_partners for select using (verification_status = 'verified' or public.can_edit_catalog() or public.can_review_catalog());
create policy "Editors manage partners" on public.market_partners for all using (public.can_edit_catalog()) with check (public.can_edit_catalog());
create policy "Dosage forms are public" on public.dosage_forms for select using (true);
create policy "Editors manage dosage forms" on public.dosage_forms for all using (public.can_edit_catalog()) with check (public.can_edit_catalog());
create policy "Published product ingredients are public" on public.product_active_ingredients for select
using (
  public.can_edit_catalog() or public.can_review_catalog() or exists (
    select 1 from public.products p where p.id = product_id and p.is_published
  )
);
create policy "Editors manage product ingredients" on public.product_active_ingredients for all using (public.can_edit_catalog()) with check (public.can_edit_catalog());
create policy "Verified published market records are public" on public.product_market_records for select
using (
  public.can_edit_catalog() or public.can_review_catalog() or (
    verification_status = 'verified' and exists (
      select 1 from public.products p where p.id = product_id and p.is_published
    )
  )
);
create policy "Editors manage market records" on public.product_market_records for all using (public.can_edit_catalog()) with check (public.can_edit_catalog());
create policy "Verified sources are public" on public.sources for select using (verification_status = 'verified' or public.can_edit_catalog() or public.can_review_catalog());
create policy "Editors manage sources" on public.sources for all using (public.can_edit_catalog()) with check (public.can_edit_catalog());
create policy "Public provenance is readable" on public.field_provenance for select using (is_public or public.can_edit_catalog() or public.can_review_catalog());
create policy "Editors manage provenance" on public.field_provenance for all using (public.can_edit_catalog()) with check (public.can_edit_catalog());
create policy "Approved interactions are public" on public.drug_interactions for select using (review_status = 'approved' or public.can_edit_catalog() or public.can_review_catalog());
create policy "Editors manage interactions" on public.drug_interactions for all using (public.can_edit_catalog()) with check (public.can_edit_catalog());
create policy "Approved published alternatives are public" on public.product_alternatives for select
using (
  public.can_edit_catalog() or public.can_review_catalog() or (
    review_status = 'approved'
    and exists (select 1 from public.products p where p.id = product_id and p.is_published)
    and exists (select 1 from public.products p where p.id = alternative_product_id and p.is_published)
  )
);
create policy "Editors manage alternatives" on public.product_alternatives for all using (public.can_edit_catalog()) with check (public.can_edit_catalog());
create policy "Staff read revisions" on public.catalog_revisions for select using (public.can_edit_catalog() or public.can_review_catalog());
create policy "Editors create revisions" on public.catalog_revisions for insert with check (public.can_edit_catalog());
create policy "Reviewers update revisions" on public.catalog_revisions for update using (public.can_review_catalog()) with check (public.can_review_catalog());
create policy "Public sync feed" on public.catalog_change_feed for select using (is_public or public.can_edit_catalog() or public.can_review_catalog());

create policy "Users manage own sync devices" on public.sync_devices for all
using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "Staff manage import batches" on public.import_batches for all
using (public.can_edit_catalog()) with check (public.can_edit_catalog());
create policy "Staff manage import errors" on public.import_errors for all
using (public.can_edit_catalog()) with check (public.can_edit_catalog());
create policy "Staff manage import rollback rows" on public.import_row_changes for all
using (public.is_admin()) with check (public.is_admin());

create policy "Users create conversations" on public.conversations for insert
with check (created_by = auth.uid());
create policy "Members and support read conversations" on public.conversations for select
using (public.can_access_conversation(id));
create policy "Creators assignees and support update conversations" on public.conversations for update
using (created_by = auth.uid() or assigned_to = auth.uid() or public.can_support_users())
with check (created_by = auth.uid() or assigned_to = auth.uid() or public.can_support_users());
create policy "Members read conversation membership" on public.conversation_members for select
using (user_id = auth.uid() or public.can_access_conversation(conversation_id));
create policy "Support manages conversation membership" on public.conversation_members for all
using (public.can_support_users()) with check (public.can_support_users());
create policy "Members read private messages" on public.conversation_messages for select
using (public.can_access_conversation(conversation_id));
create policy "Members send private messages" on public.conversation_messages for insert
with check (sender_id = auth.uid() and public.can_access_conversation(conversation_id));
create policy "Message participants read attachments" on public.message_attachments for select
using (public.can_access_message(message_id));
create policy "Message participants create attachments" on public.message_attachments for insert
with check (
  uploaded_by = auth.uid()
  and scan_status = 'pending'
  and public.can_access_message(message_id)
  and split_part(storage_path, '/', 1) = auth.uid()::text
);
create policy "Support manages attachments" on public.message_attachments for all
using (public.can_support_users()) with check (public.can_support_users());

create policy "Authorized audiences read active announcements" on public.announcements for select
using (
  public.can_support_users() or (
    is_published
    and (starts_at is null or starts_at <= now())
    and (ends_at is null or ends_at > now())
    and (
      audience = 'all'
      or (audience = 'pharmacists' and public.has_any_role(array['owner', 'admin', 'pharmacist_editor', 'reviewer']))
      or (audience = 'editors' and public.has_any_role(array['owner', 'admin', 'pharmacist_editor', 'reviewer']))
      or (audience = 'admins' and public.is_admin())
    )
  )
);
create policy "Support manages announcements" on public.announcements for all
using (public.can_support_users()) with check (public.can_support_users());
create policy "Users read own notifications" on public.notifications for select using (user_id = auth.uid());
create policy "Users update own notifications" on public.notifications for update using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "Support creates notifications" on public.notifications for insert with check (public.can_support_users());
create policy "Users manage own push devices" on public.push_devices for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "Authenticated users submit reports" on public.data_reports for insert to authenticated with check (reporter_id = auth.uid());
create policy "Users read own reports" on public.data_reports for select using (reporter_id = auth.uid() or public.can_edit_catalog() or public.can_review_catalog() or public.can_support_users());
create policy "Staff manage reports" on public.data_reports for update using (public.can_edit_catalog() or public.can_review_catalog() or public.can_support_users()) with check (public.can_edit_catalog() or public.can_review_catalog() or public.can_support_users());

-- Private buckets are accessed through authenticated policies or signed URLs.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('source-documents', 'source-documents', false, 52428800, array['application/pdf', 'image/jpeg', 'image/png', 'image/webp']),
  ('message-attachments', 'message-attachments', false, 10485760, array['image/jpeg', 'image/png', 'image/webp', 'application/pdf', 'text/plain'])
on conflict (id) do update set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

create policy "Catalog staff manage source documents" on storage.objects for all to authenticated
using (bucket_id = 'source-documents' and (public.can_edit_catalog() or public.can_review_catalog()))
with check (bucket_id = 'source-documents' and (public.can_edit_catalog() or public.can_review_catalog()));
create policy "Message participants read message files" on storage.objects for select to authenticated
using (
  bucket_id = 'message-attachments' and exists (
    select 1 from public.message_attachments a
    where a.storage_path = name
      and a.scan_status = 'clean'
      and public.can_access_message(a.message_id)
  )
);
create policy "Users upload own message files" on storage.objects for insert to authenticated
with check (
  bucket_id = 'message-attachments'
  and owner = auth.uid()
  and split_part(name, '/', 1) = auth.uid()::text
);
create policy "Users delete own message files" on storage.objects for delete to authenticated
using (
  bucket_id = 'message-attachments'
  and owner = auth.uid()
  and split_part(name, '/', 1) = auth.uid()::text
);
create policy "Support staff manage message files" on storage.objects for all to authenticated
using (bucket_id = 'message-attachments' and public.can_support_users())
with check (bucket_id = 'message-attachments' and public.can_support_users());

-- Realtime surfaces only row changes allowed by each subscriber's RLS.
do $$ begin
  alter publication supabase_realtime add table public.catalog_change_feed;
exception when duplicate_object then null; end $$;
do $$ begin
  alter publication supabase_realtime add table public.conversation_messages;
exception when duplicate_object then null; end $$;
do $$ begin
  alter publication supabase_realtime add table public.announcements;
exception when duplicate_object then null; end $$;
do $$ begin
  alter publication supabase_realtime add table public.notifications;
exception when duplicate_object then null; end $$;
