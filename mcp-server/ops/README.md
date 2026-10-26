# MCP service operations

These tools run on Node.js 22+ from the deployed service directory (`/opt/gws-mcp`); `better-sqlite3` must be installed there. The backup is a consistent `better-sqlite3` online backup, encrypted in full with AES-256-GCM. Its binary format is `GWSBKP01` (format/version), a random 12-byte nonce, ciphertext, then the 16-byte authentication tag. The format marker is authenticated as GCM additional data.

## Install

Create the service account and private paths with ownership appropriate to the deployment. Keep the backup key separate from the data-encryption key and database. As root, generate a raw 32-byte key file:

```sh
install -d -o root -g gws-mcp -m 0750 /etc/gws-mcp
umask 077
openssl rand 32 > /etc/gws-mcp/backup.key
chown root:gws-mcp /etc/gws-mcp/backup.key
chmod 0640 /etc/gws-mcp/backup.key
install -d -o gws-mcp -g gws-mcp -m 0700 /var/lib/gws-mcp/backups
```

Edit `systemd/gws-mcp-health.service` and replace `https://mcp.example.invalid` with the deployed public HTTPS origin. Install the four unit files into `/etc/systemd/system/`, then enable timers:

```sh
systemctl daemon-reload
systemctl enable --now gws-mcp-health.timer gws-mcp-backup.timer
systemctl list-timers 'gws-mcp-*'
```

The health check runs at boot and every 15 minutes. The backup runs daily, keeps 14 days, and only removes files matching its exact `gws-mcp-<UTC timestamp>-<8 hex>.sqlite.aesgcm` filename pattern. Both services run as `gws-mcp`, with systemd filesystem, privilege, and syscall restrictions. Set database and key paths in the service unit if the deployment differs.

## Run and inspect

```sh
sudo -u gws-mcp DATABASE_PATH=/var/lib/gws-mcp/data.sqlite \
  BACKUP_ENCRYPTION_KEY_FILE=/etc/gws-mcp/backup.key \
  node /opt/gws-mcp/ops/backup.mjs --output-dir /var/lib/gws-mcp/backups --retention-days 14
journalctl -u gws-mcp-backup.service -n 30 --no-pager
journalctl -u gws-mcp-health.service -n 30 --no-pager
```

Health checks request `/healthz`, `/.well-known/oauth-authorization-server`, and `/.well-known/oauth-protected-resource/mcp` from `PUBLIC_URL`. They require `offline_access` in authorization `scopes_supported` and absent from protected-resource `scopes_supported`. Timeouts default to five seconds (`HEALTH_TIMEOUT_MS`). Failure exits nonzero; response bodies and credentials are never logged.

## Restore verification

Choose a new, private destination that does not exist. The command decrypts to a private temporary file, validates SQLite with `quick_check`, then atomically publishes the verified database without replacing an existing path:

```sh
umask 077
node /opt/gws-mcp/ops/restore-verify.mjs \
  /var/lib/gws-mcp/backups/gws-mcp-YYYYMMDDTHHMMSSZ-0123abcd.sqlite.aesgcm \
  /var/lib/gws-mcp/restore-check.sqlite \
  --key-file /etc/gws-mcp/backup.key
```

The restore output contains personal data. Restrict access, remove it securely according to local policy after verification, and do not point the live service at it without an explicit recovery procedure. Keep the backup key separately: losing it makes backups unrecoverable. An on-host backup is not disaster recovery until copied off host to protected storage. Legal/privacy review of retention and recovery remains a human responsibility. These scripts do not upload, notify, or otherwise contact external services beyond the health check's configured HTTPS endpoints.

## Proxy boundary

Observe the real TCP peer of the reverse proxy before setting `TRUSTED_PROXY_CIDRS`.
Use that exact IP/CIDR, never a whole LAN or `*`. Verify that the proxy replaces
forwarded headers or appends the actual client as the nearest untrusted hop.
Probe with forged `X-Forwarded-For` values and check only keyed rate-limit
identities, never log raw client addresses or headers.

Copy `firewall.nft.example` to `/etc/gws-mcp/firewall.nft` after replacing the
example proxy address and port. Install `systemd/gws-mcp-firewall.service` and
enable it. It only filters the MCP port: loopback and the named proxy are
allowed, other IPv4/IPv6 peers are dropped. Add a `Requires`/`After` dependency
on this unit to the application service. Confirm public HTTPS still works and
direct LAN access with a forged Host/forwarded header fails before enabling
client-IP trust.

The snapshot plaintext is staged in a private runtime directory; systemd removes
that directory when the backup unit stops, including after an abnormal exit.
Only encrypted snapshots are published into the durable backup directory.
Restore verification invalidates all saved OAuth tokens, browser sessions,
authorization codes and action confirmations before publishing: reconnect clients
after recovery. Old backups must never resurrect a revoked grant.
