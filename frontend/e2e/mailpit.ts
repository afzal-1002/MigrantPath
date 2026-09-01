// Talks to the developer's/CI's real Mailpit instance (docker-compose.yml) - the same
// one the backend's `local` profile sends mail through. Unlike the backend's own
// integration tests (which spin up a throwaway Testcontainers Mailpit), E2E exercises
// the actual local dev stack end to end, so it uses that same Mailpit deliberately.
const MAILPIT_BASE = 'http://localhost:8025';

export async function deleteAllMessages(): Promise<void> {
  await fetch(`${MAILPIT_BASE}/api/v1/messages`, { method: 'DELETE' });
}

/** Polls Mailpit's search API for the newest message to `toEmail` (delivery isn't
 * synchronous with the API call that triggered it) and returns its HTML body. */
export async function findLatestMessageTo(toEmail: string): Promise<string> {
  const query = encodeURIComponent(`to:${toEmail}`);
  for (let attempt = 0; attempt < 40; attempt++) {
    const searchResponse = await fetch(`${MAILPIT_BASE}/api/v1/search?query=${query}`);
    const searchResult = await searchResponse.json();
    if (Array.isArray(searchResult.messages) && searchResult.messages.length > 0) {
      // Sorted explicitly rather than trusting the API's default order - a test
      // sending two emails to the same address (e.g. verify, then a later
      // forgot-password) needs the newest one, not just "some" match.
      const newest = [...searchResult.messages].sort(
        (a, b) => new Date(b.Created).getTime() - new Date(a.Created).getTime(),
      )[0];
      const messageResponse = await fetch(`${MAILPIT_BASE}/api/v1/message/${newest.ID}`);
      const message = await messageResponse.json();
      return message.HTML as string;
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`No email arrived for ${toEmail} within the timeout`);
}

export function extractTokenFromHtml(html: string): string {
  const match = html.match(/token=([\w-]+)/);
  if (!match) {
    throw new Error('Email body did not contain a token= link');
  }
  return match[1];
}
