/**
 * Mirrors `dev.or2.central.account.Rights` (central-common). `accounts.rights` and
 * `worlds.login_min_rights_token` must hold these canonical names, because game servers read them
 * with `Rights.fromRightsColumn` / `Rights.parse`.
 */
export type RightsName = "NONE" | "MOD" | "ADMINISTRATOR";

export const RIGHTS_OPTIONS: { value: RightsName; label: string }[] = [
  { value: "NONE", label: "Player" },
  { value: "MOD", label: "Moderator" },
  { value: "ADMINISTRATOR", label: "Administrator" },
];

const LEVEL: Record<RightsName, number> = { NONE: 0, MOD: 2, ADMINISTRATOR: 3 };

/** Same aliases as `Rights.parse`, including legacy `modlevel.*` tokens. */
export function parseRights(raw: unknown): RightsName {
  const t = String(raw ?? "").trim().replace(/^modlevel\./i, "").toUpperCase();
  if (t === "MOD" || t === "MODERATOR") return "MOD";
  if (t === "ADMIN" || t === "ADMINISTRATOR" || t === "OWNER") return "ADMINISTRATOR";
  return "NONE";
}

/** Same as `Rights.fromRightsColumn`: highest of a comma-separated list. */
export function rightsFromColumn(raw: unknown): RightsName {
  let best: RightsName = "NONE";
  for (const part of String(raw ?? "").split(",")) {
    const r = parseRights(part);
    if (LEVEL[r] > LEVEL[best]) best = r;
  }
  return best;
}

/** Value stored in `accounts.rights`; players keep the column default (empty string). */
export function rightsColumnValue(r: RightsName): string {
  return r === "NONE" ? "" : r;
}

export function rightsLabel(r: RightsName): string {
  return RIGHTS_OPTIONS.find((o) => o.value === r)?.label ?? r;
}
