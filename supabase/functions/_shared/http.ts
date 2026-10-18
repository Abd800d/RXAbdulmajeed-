import { createClient } from 'npm:@supabase/supabase-js@2';

export const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type, x-device-id',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

export const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers: { ...corsHeaders, 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' },
});

export const handleOptions = (request: Request): Response | undefined =>
  request.method === 'OPTIONS' ? new Response('ok', { headers: corsHeaders }) : undefined;

export function adminClient() {
  const url = Deno.env.get('SUPABASE_URL');
  const serviceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
  if (!url || !serviceKey) throw new Error('Supabase server configuration is incomplete.');
  return createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
}

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('');
}

export async function enforceRateLimit(request: Request, endpoint: string, limit: number, windowMinutes = 60): Promise<Response | undefined> {
  const deviceId = cleanText(request.headers.get('x-device-id') || 'unknown-device', 200);
  const forwarded = cleanText(
    request.headers.get('x-forwarded-for')?.split(',')[0]?.trim()
      || request.headers.get('x-real-ip')
      || 'unknown-ip',
    100,
  );
  const client = adminClient();
  const checks = [
    { hash: await sha256(`device:${deviceId}`), endpoint: `${endpoint}:device`, allowed: limit },
    // A second, more generous network quota prevents trivial bypass by rotating
    // client-generated device IDs while avoiding harsh limits for shared networks.
    ...(forwarded === 'unknown-ip' ? [] : [{ hash: await sha256(`network:${forwarded}`), endpoint: `${endpoint}:network`, allowed: limit * 8 }]),
  ];
  for (const check of checks) {
    const { data, error } = await client.rpc('consume_api_quota', {
      p_identifier_hash: check.hash,
      p_endpoint: check.endpoint,
      p_limit: check.allowed,
      p_window_minutes: windowMinutes,
    });
    if (error) return json({ error: 'Rate-limit service unavailable.' }, 503);
    if (!data) return json({ error: 'Request limit reached. Please try again later.' }, 429);
  }
  return undefined;
}

export function cleanText(value: unknown, maxLength = 10_000): string {
  return String(value ?? '').replace(/\0/g, '').trim().slice(0, maxLength);
}
