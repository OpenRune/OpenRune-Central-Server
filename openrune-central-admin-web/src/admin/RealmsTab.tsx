import { useCallback, useEffect, useState } from "react";
import type { BridgeDb } from "../bridge/BridgeDb";
import { REALMS_LIST } from "./queries";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

type RealmRow = Record<string, unknown>;

const emptyRealm = () => ({
  name: "",
  description: "",
  login_message: "",
  login_broadcast: "",
  spawn_coord: "0_50_50_21_18",
  respawn_coord: "0_50_50_21_18",
  dev_mode: "0",
  require_registration: "1",
  auto_assign_display_names: "0",
  player_xp_rate_in_hundreds: "100",
  global_xp_rate_in_hundreds: "100",
});

export function RealmsTab({ db }: { db: BridgeDb }) {
  const [rows, setRows] = useState<RealmRow[]>([]);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState(emptyRealm);

  const load = useCallback(async () => {
    setErr(null);
    const r = await db.query(REALMS_LIST);
    setRows(r.rows);
  }, [db]);

  useEffect(() => {
    load().catch((e) => setErr(e instanceof Error ? e.message : String(e)));
  }, [load]);

  const openCreate = () => {
    setEditingId(null);
    setForm(emptyRealm());
    setInfo(null);
    setErr(null);
    setDialogOpen(true);
  };

  const openEdit = (row: RealmRow) => {
    const id = Number(row.realm_id);
    setEditingId(id);
    setForm({
      name: String(row.name ?? ""),
      description: String(row.description ?? ""),
      login_message: String(row.login_message ?? ""),
      login_broadcast: String(row.login_broadcast ?? ""),
      spawn_coord: String(row.spawn_coord ?? ""),
      respawn_coord: String(row.respawn_coord ?? ""),
      dev_mode: String(row.dev_mode ?? "0"),
      require_registration: String(row.require_registration ?? "0"),
      auto_assign_display_names: String(row.auto_assign_display_names ?? "0"),
      player_xp_rate_in_hundreds: String(row.player_xp_rate_in_hundreds ?? "100"),
      global_xp_rate_in_hundreds: String(row.global_xp_rate_in_hundreds ?? "100"),
    });
    setInfo(null);
    setErr(null);
    setDialogOpen(true);
  };

  const save = async () => {
    setBusy(true);
    setErr(null);
    setInfo(null);
    try {
      const params = [
        form.name.trim(),
        form.description.trim() || null,
        form.login_message.trim() || null,
        form.login_broadcast.trim() || null,
        form.spawn_coord.trim(),
        form.respawn_coord.trim(),
        Number(form.dev_mode) ? 1 : 0,
        Number(form.require_registration) ? 1 : 0,
        Number(form.auto_assign_display_names) ? 1 : 0,
        Number(form.player_xp_rate_in_hundreds) || 100,
        Number(form.global_xp_rate_in_hundreds) || 100,
      ];
      if (editingId == null) {
        const sql = `
INSERT INTO realms (
  name, description, login_message, login_broadcast, spawn_coord, respawn_coord,
  dev_mode, require_registration, auto_assign_display_names,
  player_xp_rate_in_hundreds, global_xp_rate_in_hundreds
)
VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
RETURNING realm_id
`.trim();
        const r = await db.query(sql, params);
        const id = Number(r.rows[0]?.realm_id);
        setInfo(`Created realm_id ${id}.`);
        setEditingId(id);
      } else {
        const sql = `
UPDATE realms
SET
  name = $1,
  description = $2,
  login_message = $3,
  login_broadcast = $4,
  spawn_coord = $5,
  respawn_coord = $6,
  dev_mode = $7,
  require_registration = $8,
  auto_assign_display_names = $9,
  player_xp_rate_in_hundreds = $10,
  global_xp_rate_in_hundreds = $11
WHERE realm_id = $12
`.trim();
        await db.query(sql, [...params, editingId]);
        setInfo(`Updated realms ${editingId}.`);
      }
      await load();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    if (editingId == null) {
      return;
    }
    setBusy(true);
    setErr(null);
    setInfo(null);
    try {
      const cnt = await db.query("SELECT COUNT(*)::int AS c FROM worlds WHERE realm_id = $1", [editingId]);
      const c = Number(cnt.rows[0]?.c ?? 0);
      if (c > 0) {
        setErr(`Cannot delete: ${c} worlds(s) still reference this realms.`);
        setBusy(false);
        return;
      }
      const del = await db.query(
        `DELETE FROM realms WHERE realm_id = $1 AND NOT EXISTS (SELECT 1 FROM worlds w WHERE w.realm_id = realms.realm_id)`,
        [editingId],
      );
      if ((del.rowCount ?? 0) < 1) {
        setErr("Delete affected 0 rows (realms missing or still referenced).");
      } else {
        setInfo(`Deleted realms ${editingId}.`);
        setDialogOpen(false);
        setEditingId(null);
        await load();
      }
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={() => load().catch((e) => setErr(String(e)))}>
          Refresh
        </Button>
        <Button type="button" size="sm" onClick={openCreate}>
          Add realms
        </Button>
      </div>
      {err && !dialogOpen && <p className="text-sm text-destructive">{err}</p>}
      {info && !dialogOpen && <p className="text-sm text-green-400">{info}</p>}
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-16">id</TableHead>
            <TableHead>name</TableHead>
            <TableHead className="w-20">dev</TableHead>
            <TableHead>xp / gxp</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => (
            <TableRow
              key={String(row.realm_id)}
              className="cursor-pointer"
              onClick={() => openEdit(row)}
            >
              <TableCell className="font-mono text-xs">{String(row.realm_id)}</TableCell>
              <TableCell>
                <code className="text-sm">{String(row.name)}</code>
              </TableCell>
              <TableCell>{String(row.dev_mode)}</TableCell>
              <TableCell className="text-muted-foreground text-xs">
                {String(row.player_xp_rate_in_hundreds)} / {String(row.global_xp_rate_in_hundreds)}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <Dialog
        open={dialogOpen}
        onOpenChange={(o) => {
          setDialogOpen(o);
          if (!o) {
            setErr(null);
            setInfo(null);
          }
        }}
      >
        <DialogContent className="flex max-h-[92vh] w-[min(96vw,56rem)] max-w-none flex-col gap-0 overflow-hidden p-0 sm:max-w-none">
          <DialogHeader className="space-y-0 border-b px-6 py-4 text-left">
            <DialogTitle>{editingId == null ? "New realms" : `Edit realms ${editingId}`}</DialogTitle>
            <DialogDescription className="sr-only">Realm form</DialogDescription>
          </DialogHeader>
          <ScrollArea className="max-h-[min(78vh,760px)] px-6">
            <div className="space-y-3 py-4 pr-3">
              {err && <p className="text-sm text-destructive">{err}</p>}
              {info && <p className="text-sm text-green-400">{info}</p>}
              <div className="space-y-2">
                <Label htmlFor="realms-name">name</Label>
                <Input id="realms-name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="realms-desc">description</Label>
                <Input id="realms-desc" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="realms-login-msg">login_message</Label>
                <Input
                  id="realms-login-msg"
                  value={form.login_message}
                  onChange={(e) => setForm({ ...form, login_message: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="realms-login-broadcast">login_broadcast</Label>
                <Input
                  id="realms-login-broadcast"
                  value={form.login_broadcast}
                  onChange={(e) => setForm({ ...form, login_broadcast: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="realms-spawn">spawn_coord</Label>
                <Input id="realms-spawn" value={form.spawn_coord} onChange={(e) => setForm({ ...form, spawn_coord: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="realms-respawn">respawn_coord</Label>
                <Input
                  id="realms-respawn"
                  value={form.respawn_coord}
                  onChange={(e) => setForm({ ...form, respawn_coord: e.target.value })}
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-2">
                  <Label htmlFor="realms-dev">dev_mode (0/1)</Label>
                  <Input id="realms-dev" value={form.dev_mode} onChange={(e) => setForm({ ...form, dev_mode: e.target.value })} />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="realms-reg">require_registration (0/1)</Label>
                  <Input
                    id="realms-reg"
                    value={form.require_registration}
                    onChange={(e) => setForm({ ...form, require_registration: e.target.value })}
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="realms-auto">auto_assign_display_names (0/1)</Label>
                <Input
                  id="realms-auto"
                  value={form.auto_assign_display_names}
                  onChange={(e) => setForm({ ...form, auto_assign_display_names: e.target.value })}
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-2">
                  <Label htmlFor="realms-pxp">player_xp_rate_in_hundreds</Label>
                  <Input
                    id="realms-pxp"
                    value={form.player_xp_rate_in_hundreds}
                    onChange={(e) => setForm({ ...form, player_xp_rate_in_hundreds: e.target.value })}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="realms-gxp">global_xp_rate_in_hundreds</Label>
                  <Input
                    id="realms-gxp"
                    value={form.global_xp_rate_in_hundreds}
                    onChange={(e) => setForm({ ...form, global_xp_rate_in_hundreds: e.target.value })}
                  />
                </div>
              </div>
            </div>
          </ScrollArea>
          <DialogFooter className="flex-row flex-wrap gap-2 border-t px-6 py-4 sm:justify-between">
            <div className="flex gap-2">
              {editingId != null && (
                <Button type="button" variant="destructive" size="sm" disabled={busy} onClick={remove}>
                  Delete
                </Button>
              )}
            </div>
            <div className="flex gap-2">
              <Button type="button" variant="outline" size="sm" onClick={() => setDialogOpen(false)}>
                Close
              </Button>
              <Button type="button" size="sm" disabled={busy || !form.name.trim()} onClick={save}>
                {busy ? "Saving…" : "Save"}
              </Button>
            </div>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
