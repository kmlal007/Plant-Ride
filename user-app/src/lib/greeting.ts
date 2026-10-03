export function greeting(name?: string | null): string {
  const h = new Date().getHours();
  const part = h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
  const first = name?.replace(/\(.*\)/, '').trim().split(/\s+/)[0];
  return first ? `${part}, ${first}` : part;
}
