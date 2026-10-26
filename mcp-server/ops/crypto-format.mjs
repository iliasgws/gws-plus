import { createCipheriv, createDecipheriv, randomBytes } from 'node:crypto';
import { open, readFile, rm } from 'node:fs/promises';

// Wire format: 8-byte magic/version, 12-byte nonce, ciphertext, 16-byte GCM tag.
export const MAGIC = Buffer.from('GWSBKP01');
const NONCE_BYTES = 12;
const TAG_BYTES = 16;

export async function loadKey(file) {
  const key = await readFile(file);
  if (key.length !== 32) throw new Error('Backup key file must contain exactly 32 raw bytes');
  return key;
}

export async function encryptFile(source, destination, key) {
  const nonce = randomBytes(NONCE_BYTES);
  const cipher = createCipheriv('aes-256-gcm', key, nonce);
  cipher.setAAD(MAGIC);
  const out = await open(destination, 'wx', 0o600);
  try {
    await out.writeFile(MAGIC);
    await out.writeFile(nonce);
    // GCM tag follows the ciphertext; staged to a private file before publish.
    const input = await import('node:fs').then(({ createReadStream }) => createReadStream(source));
    for await (const chunk of input) await out.writeFile(cipher.update(chunk));
    await out.writeFile(cipher.final());
    await out.writeFile(cipher.getAuthTag());
    await out.sync();
  } catch (error) {
    await out.close();
    await rm(destination, { force: true });
    throw error;
  }
  await out.close();
}

export async function decryptFile(source, destination, key) {
  const handle = await open(source, 'r');
  const stat = await handle.stat();
  const headerSize = MAGIC.length + NONCE_BYTES;
  if (stat.size < headerSize + TAG_BYTES) { await handle.close(); throw new Error('Invalid encrypted backup'); }
  const header = Buffer.alloc(headerSize);
  const tag = Buffer.alloc(TAG_BYTES);
  await handle.read(header, 0, header.length, 0);
  await handle.read(tag, 0, tag.length, stat.size - TAG_BYTES);
  await handle.close();
  if (!header.subarray(0, MAGIC.length).equals(MAGIC)) throw new Error('Unsupported encrypted backup format');
  const decipher = createDecipheriv('aes-256-gcm', key, header.subarray(MAGIC.length));
  decipher.setAAD(MAGIC);
  decipher.setAuthTag(tag);
  const out = await open(destination, 'wx', 0o600);
  try {
    const { createReadStream } = await import('node:fs');
    const input = createReadStream(source, { start: headerSize, end: stat.size - TAG_BYTES - 1 });
    for await (const chunk of input) await out.writeFile(decipher.update(chunk));
    await out.writeFile(decipher.final());
    await out.sync();
  } catch (error) {
    await out.close();
    await rm(destination, { force: true });
    throw error;
  }
  await out.close();
}
