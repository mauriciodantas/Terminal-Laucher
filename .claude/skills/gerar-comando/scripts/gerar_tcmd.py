#!/usr/bin/env python3
"""Gera (ou soma a) um pacote .tcmd para a barra de comando do Terminal Launcher."""
import argparse
import json
import os
import sys

RESERVED = {"abrir", "alarme", "calc", "ligar", "rota", "t", "c"}
ARGS = ["NONE", "TEXT", "CONTACT", "CONTACT_AND_TEXT"]
MAX_ITEMS = 200
MAX_BYTES = 256 * 1024


def fail(msg):
    sys.exit(f"ERRO: {msg}")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--letter", required=True)
    p.add_argument("--label", required=True)
    p.add_argument("--template", required=True)
    p.add_argument("--arg", default="TEXT", choices=ARGS)
    p.add_argument("--kind", default="INTENT", choices=["INTENT", "SHORTCUT"])
    p.add_argument("--package", action="append", default=[])
    p.add_argument("--sms-fallback", action="store_true")
    p.add_argument("--action", default="android.intent.action.VIEW")
    p.add_argument("--mime")
    p.add_argument("--extra")
    p.add_argument("--alias", action="append", default=[], metavar="NOME=EXPANSAO")
    p.add_argument("--merge", action="store_true", help="soma a um .tcmd existente em --out")
    p.add_argument("--out")
    a = p.parse_args()

    letter = a.letter.strip().lower()
    if not letter:
        fail("informe uma letra")
    if not letter.isalnum():
        fail("use apenas letras e números na letra")
    if letter in RESERVED:
        fail(f'"{letter}" já é um comando do sistema')
    if a.kind == "SHORTCUT" and len(a.package) != 1:
        fail("SHORTCUT exige exatamente um --package")
    if a.arg == "NONE" and "{" in a.template and a.kind == "INTENT":
        fail("arg NONE não preenche placeholders; remova-os do template")
    if a.arg == "TEXT" and "{text}" not in a.template and not a.extra:
        fail("arg TEXT precisa de {text} no template (ou --extra)")

    out = a.out or os.path.join("comandos", f"{letter}.tcmd")
    pack = {"format": "terminal-commands", "version": 1, "actions": [], "aliases": []}
    if a.merge and os.path.exists(out):
        pack = json.load(open(out, encoding="utf-8"))
        if pack.get("format") != "terminal-commands":
            fail("o arquivo existente não é um pacote de comandos")
    if any(x["letter"] == letter for x in pack["actions"]):
        fail(f'"{letter}" já está no pacote')

    pack["actions"].append({
        "letter": letter,
        "label": a.label,
        "kind": a.kind,
        "template": a.template,
        "packages": a.package,
        "arg": a.arg,
        "sms": a.sms_fallback,
        "action": a.action,
        "mime": a.mime,
        "extra": a.extra,
    })
    for item in a.alias:
        name, _, exp = item.partition("=")
        name, exp = name.strip().lower(), exp.strip()
        if not name.isalnum() or not exp:
            fail(f"alias inválido: {item!r}")
        if name in RESERVED or any(x["letter"] == name for x in pack["actions"]):
            fail(f'alias "{name}" conflita com um comando')
        if exp.split()[0].lower() == name:
            fail("o alias não pode começar pelo próprio nome")
        if any(x["name"] == name for x in pack["aliases"]):
            fail(f'alias "{name}" já está no pacote')
        pack["aliases"].append({"name": name, "expansion": exp})

    if len(pack["actions"]) > MAX_ITEMS or len(pack["aliases"]) > MAX_ITEMS:
        fail("pacote com itens demais")
    text = json.dumps(pack, indent=2, ensure_ascii=False) + "\n"
    if len(text.encode()) > MAX_BYTES:
        fail("pacote maior que 256 KB")
    os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        f.write(text)
    print(f"OK: {out} ({len(pack['actions'])} ação(ões), {len(pack['aliases'])} alias(es)) · uso: {letter}")


if __name__ == "__main__":
    main()
