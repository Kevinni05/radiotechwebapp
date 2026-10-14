export async function readAuthResponse(response) {
  let data;
  try { data = await response.json(); } catch { /* Gateways can return plain text. */ }
  if (data && typeof data === 'object' && !Array.isArray(data)) return data;
  const message = response.status === 429
    ? 'Troppi tentativi. Attendi prima di riprovare.'
    : 'Non è stato possibile completare l’accesso. Riprova tra poco.';
  if (response.ok) throw new Error(message);
  return { message };
}
