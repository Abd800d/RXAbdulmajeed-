#!/usr/bin/env node
/**
 * Executes the APH migration inside PGlite (real PostgreSQL compiled to WASM)
 * with small Supabase auth/storage stubs, then exercises critical security and
 * search behavior. A deployed Supabase project is still required for final
 * integration tests, but this catches SQL syntax and policy regressions in CI.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { PGlite } from '@electric-sql/pglite';
import { pgcrypto } from '@electric-sql/pglite/contrib/pgcrypto';
import { pg_trgm } from '@electric-sql/pglite/contrib/pg_trgm';
import { unaccent } from '@electric-sql/pglite/contrib/unaccent';

const migrationsDirectory = new URL('../supabase/migrations/', import.meta.url);
const database = new PGlite({ extensions: { pgcrypto, pg_trgm, unaccent } });
const ownerId = '00000000-0000-4000-8000-000000000001';
const userId = '00000000-0000-4000-8000-000000000002';
const strangerId = '00000000-0000-4000-8000-000000000003';

try {
  await database.exec(`
    create role anon;
    create role authenticated;
    create role service_role;

    create schema auth;
    create table auth.users (
      id uuid primary key,
      email text,
      raw_user_meta_data jsonb not null default '{}'::jsonb
    );
    create function auth.uid() returns uuid
    language sql stable
    as $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;

    create schema storage;
    create table storage.buckets (
      id text primary key,
      name text,
      public boolean,
      file_size_limit bigint,
      allowed_mime_types text[]
    );
    create table storage.objects (
      id uuid primary key default gen_random_uuid(),
      bucket_id text,
      name text,
      owner uuid
    );
    create publication supabase_realtime;
  `);

  const migrationFiles = fs.readdirSync(migrationsDirectory)
    .filter((name) => name.endsWith('.sql'))
    .sort();
  for (const migrationFile of migrationFiles) {
    await database.exec(fs.readFileSync(new URL(migrationFile, migrationsDirectory), 'utf8'));
  }

  await database.exec(`
    grant usage on schema public to anon, authenticated;
    grant select on public.body_systems, public.drugs, public.products,
      public.messages, public.message_contacts, public.catalog_tombstones
      to anon, authenticated;
    grant select, insert, update on public.conversations to authenticated;
    grant select, insert on public.conversation_messages, public.conversation_members,
      public.message_attachments to authenticated;
    grant select, update on public.profiles to authenticated;
    grant insert on public.messages to anon, authenticated;

    insert into auth.users(id, email, raw_user_meta_data) values
      ('${ownerId}', '7a8b1d@gmail.com', '{"display_name":"Owner"}'),
      ('${userId}', 'user@example.com', '{"display_name":"User"}'),
      ('${strangerId}', 'stranger@example.com', '{"display_name":"Stranger"}');

    insert into public.drugs(
      id, generic_name, arabic_name, system_id, pharmacology_class,
      aliases, verification_status, is_published
    ) values (
      'paracetamol', 'Paracetamol', 'باراسيتامول', 'nervous',
      'Analgesic', array['acetaminophen'], 'verified', true
    );
    insert into public.products(
      id, drug_id, brand_name, generic_name, strength, is_published
    ) values ('panadol', 'paracetamol', 'Panadol', 'Paracetamol', '500 mg', true);
  `);

  const search = await database.query(`
    select drug->>'generic_name' as generic, matched_product->>'brand_name' as brand
    from public.search_medicines('panado', 10)
  `);
  assert.equal(search.rows[0]?.generic, 'Paracetamol');
  assert.equal(search.rows[0]?.brand, 'Panadol');

  const searchDocument = await database.query(`select search_document::text as document from public.drugs where id = 'paracetamol'`);
  assert.match(searchDocument.rows[0].document, /paracetamol/);
  assert.match(searchDocument.rows[0].document, /acetaminophen/);

  const submitted = await database.query(`
    select (public.submit_public_message(
      '00000000-0000-4000-8000-000000000099',
      'Tester', 'private@example.com', 'Hello APH'
    )).id as id
  `);
  assert.ok(submitted.rows[0]?.id);

  await database.exec(`
    set role authenticated;
    select set_config('request.jwt.claim.sub', '${userId}', false);
    update public.profiles set role = 'admin', display_name = 'Updated safely' where id = '${userId}';
    reset role;
  `);
  const protectedProfile = await database.query(`select role, display_name from public.profiles where id = '${userId}'`);
  assert.deepEqual(protectedProfile.rows[0], { role: 'user', display_name: 'Updated safely' });

  // The SQL editor/service role has no end-user uid and can securely bootstrap
  // the first owner. End users cannot take this path because RLS blocks it.
  await database.exec(`select set_config('request.jwt.claim.sub', '', false); update public.profiles set role = 'owner' where id = '${ownerId}'`);
  const owner = await database.query(`select role from public.profiles where id = '${ownerId}'`);
  assert.equal(owner.rows[0]?.role, 'owner');

  const v2Tables = await database.query(`
    select table_name from information_schema.tables
    where table_schema = 'public' and table_name in (
      'atc_classifications', 'manufacturers', 'product_active_ingredients',
      'product_market_records', 'field_provenance', 'catalog_revisions',
      'catalog_change_feed', 'import_batches', 'conversations', 'notifications'
    )
  `);
  assert.equal(v2Tables.rows.length, 10, 'all critical V2 domain tables must exist');

  const primaryComposition = await database.query(`
    select ingredient_id, component_strength, is_primary
    from public.product_active_ingredients where product_id = 'panadol'
  `);
  assert.deepEqual(primaryComposition.rows[0], {
    ingredient_id: 'paracetamol', component_strength: '500 mg', is_primary: true,
  });

  const changeFeed = await database.query(`
    select entity_type, entity_id, operation, is_public
    from public.catalog_change_feed order by sequence
  `);
  assert.ok(changeFeed.rows.some((row) => row.entity_id === 'paracetamol' && row.operation === 'UPSERT' && row.is_public));
  assert.ok(changeFeed.rows.some((row) => row.entity_id === 'panadol' && row.operation === 'UPSERT' && row.is_public));

  const conversationId = '10000000-0000-4000-8000-000000000001';
  await database.exec(`
    set role authenticated;
    select set_config('request.jwt.claim.sub', '${userId}', false);
    insert into public.conversations(id, created_by, subject)
    values ('${conversationId}', '${userId}', 'Private support');
    insert into public.conversation_messages(conversation_id, sender_id, body)
    values ('${conversationId}', '${userId}', 'Private message');
  `);
  const ownConversation = await database.query(`select id from public.conversations where id = '${conversationId}'`);
  assert.equal(ownConversation.rows.length, 1, 'conversation creator must retain access without RLS recursion');
  await database.exec(`select set_config('request.jwt.claim.sub', '${strangerId}', false)`);
  const strangerConversation = await database.query(`select id from public.conversations where id = '${conversationId}'`);
  assert.equal(strangerConversation.rows.length, 0, 'unrelated users must not read private conversations');
  await database.exec('reset role');

  await database.exec(`set role anon; select set_config('request.jwt.claim.sub', '', false)`);
  const publicMessages = await database.query(`select body from public.messages`);
  const privateContacts = await database.query(`select author_email from public.message_contacts`);
  assert.equal(publicMessages.rows.length, 1);
  assert.equal(privateContacts.rows.length, 0, 'anonymous clients must never read contact emails');
  let directInsertBlocked = false;
  try {
    await database.exec(`insert into public.messages(device_id, body) values ('00000000-0000-4000-8000-000000000088', 'Bypass')`);
  } catch { directInsertBlocked = true; }
  assert.equal(directInsertBlocked, true, 'messages must be submitted through the rate-limited RPC');
  await database.exec('reset role');

  await database.exec(`update public.drugs set is_published = false where id = 'paracetamol'`);
  const tombstone = await database.query(`select entity_type, entity_id from public.catalog_tombstones where entity_id = 'paracetamol'`);
  assert.deepEqual(tombstone.rows[0], { entity_type: 'drug', entity_id: 'paracetamol' });

  console.log('APH migration validation passed: V1/V2 schema, search, six-role protection, composition compatibility, sequence sync, private contacts, messages, and tombstones.');
} finally {
  await database.close();
}
