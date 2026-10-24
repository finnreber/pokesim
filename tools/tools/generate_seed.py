#!/usr/bin/env python3
"""
One-time generator: PokeAPI -> Flyway migration (V2__seed_gen1_firered.sql).

Why a generator instead of a runtime import?
  * The deployed API never calls PokeAPI (no runtime dependency, no rate limits, deterministic data).
  * The SQL is committed, so every environment (local, CI, Render) gets identical data from Flyway.
  * Raw API responses are cached in tools/.cache/, so re-running is offline and polite to PokeAPI.

Run:  python tools/generate_seed.py            (from the repo root)
Test: python -m unittest discover -s tools -p "test_*.py"

Generation-awareness: PokeAPI returns CURRENT data (Gen 9 values). FireRed is Gen 3, so this script
applies `past_types` (species) and `past_values` (moves) to rewind to the target generation.
"""
import argparse
import datetime
import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

API = "https://pokeapi.co/api/v2"
TOOLS_DIR = Path(__file__).resolve().parent
ROOT = TOOLS_DIR.parent
CACHE_DIR = TOOLS_DIR / ".cache"
DEFAULT_OUT = ROOT / "src/main/resources/db/migration/V2__seed_gen1_firered.sql"
DEFAULT_REPORT = TOOLS_DIR / "seed_report.txt"

GENERATIONS = {"generation-i": 1, "generation-ii": 2, "generation-iii": 3, "generation-iv": 4,
               "generation-v": 5, "generation-vi": 6, "generation-vii": 7, "generation-viii": 8,
               "generation-ix": 9}

# Chronological order of games. Unknown (newer) version groups are treated as "later than everything".
VERSION_GROUPS = ["red-blue", "yellow", "gold-silver", "crystal", "ruby-sapphire", "emerald",
                  "firered-leafgreen", "colosseum", "xd", "diamond-pearl", "platinum",
                  "heartgold-soulsilver", "black-white", "black-2-white-2", "x-y",
                  "omega-ruby-alpha-sapphire", "sun-moon", "ultra-sun-ultra-moon",
                  "lets-go-pikachu-lets-go-eevee", "sword-shield", "the-isle-of-armor",
                  "the-crown-tundra", "brilliant-diamond-and-shining-pearl", "legends-arceus",
                  "scarlet-violet", "the-teal-mask", "the-indigo-disk"]

# PokeAPI move "meta.category" values the engine can represent (secondary effects we cannot model are
# stripped and listed in the report). Everything else (heal, stat-only, OHKO, field effects...) is skipped.
INCLUDED_CATEGORIES = {"damage", "ailment", "damage-ailment", "damage-lower", "damage-raise"}

# Mechanics the engine does not implement yet. Damaging moves here would behave WRONGLY if seeded
# (e.g. a two-turn move acting instantly), so they are excluded by name. Shrink this list as the engine grows.
UNSUPPORTED_MOVES = {
    # two-turn / recharge
    "solar-beam", "sky-attack", "razor-wind", "skull-bash", "fly", "dig", "dive", "bounce",
    "hyper-beam", "blast-burn", "frenzy-plant", "hydro-cannon", "focus-punch", "bide",
    # multi-hit / locked-in
    "double-slap", "comet-punch", "fury-attack", "pin-missile", "barrage", "spike-cannon",
    "fury-swipes", "double-kick", "twineedle", "bonemerang", "triple-kick", "bone-rush",
    "thrash", "petal-dance", "outrage", "rollout", "ice-ball", "uproar", "rage",
    # variable power or conditional
    "return", "frustration", "hidden-power", "present", "magnitude", "flail", "reversal",
    "facade", "revenge", "pursuit", "fury-cutter", "beat-up", "spit-up", "weather-ball",
    "eruption", "water-spout", "snore", "dream-eater", "false-swipe", "fake-out",
    # self-KO, delayed, reflect-damage
    "self-destruct", "explosion", "future-sight", "doom-desire", "counter", "mirror-coat",
    "struggle",
}

LEARN_METHODS = {"level-up", "machine", "tutor"}   # egg moves are not learnable on a caught Pokemon
AILMENT_MAP = {"paralysis": "PARALYSIS", "sleep": "SLEEP", "freeze": "FREEZE", "burn": "BURN",
               "poison": "POISON"}
BADLY_POISONS = {"toxic", "poison-fang"}           # PokeAPI calls these plain "poison"
STAT_NAMES = {"hp": "hp", "attack": "attack", "defense": "defense", "special-attack": "sp_attack",
              "special-defense": "sp_defense", "speed": "speed"}


# ----------------------------------------------------------------------------- pure helpers (unit tested)

def gen_number(name):
    return GENERATIONS[name]


def vg_order(name):
    try:
        return VERSION_GROUPS.index(name)
    except ValueError:
        return len(VERSION_GROUPS)   # newer than anything listed


def generation_of_dex(dex):
    for upper, gen in ((151, 1), (251, 2), (386, 3), (493, 4), (649, 5), (721, 6), (809, 7), (905, 8)):
        if dex <= upper:
            return gen
    return 9


def valid_types(target_gen):
    base = {"NORMAL", "FIRE", "WATER", "ELECTRIC", "GRASS", "ICE", "FIGHTING", "POISON", "GROUND",
            "FLYING", "PSYCHIC", "BUG", "ROCK", "GHOST", "DRAGON"}
    if target_gen >= 2:
        base |= {"DARK", "STEEL"}
    if target_gen >= 6:
        base |= {"FAIRY"}
    return base


def resolve_types(pokemon, target_gen):
    """past_types entry = the types that applied up to and including that generation."""
    candidates = [(gen_number(e["generation"]["name"]), e["types"])
                  for e in pokemon.get("past_types", [])
                  if gen_number(e["generation"]["name"]) >= target_gen]
    raw = min(candidates, key=lambda c: c[0])[1] if candidates else pokemon["types"]
    return [t["type"]["name"].upper() for t in sorted(raw, key=lambda t: t["slot"])]


def resolve_base_stats(pokemon):
    stats = {STAT_NAMES[s["stat"]["name"]]: s["base_stat"] for s in pokemon["stats"]}
    missing = set(STAT_NAMES.values()) - set(stats)
    if missing:
        raise ValueError(f"{pokemon['name']}: missing stats {missing}")
    return stats


def learnable_moves(pokemon, version_group):
    return {m["move"]["name"] for m in pokemon["moves"]
            for d in m["version_group_details"]
            if d["version_group"]["name"] == version_group and d["move_learn_method"]["name"] in LEARN_METHODS}


def resolve_move_values(move, version_group):
    """Start from current values, then rewind: for each field the EARLIEST past_values entry at or after
    the target game that mentions it holds the value that was in effect at the target game."""
    values = {"power": move.get("power"), "accuracy": move.get("accuracy"), "pp": move.get("pp"),
              "type": move["type"]["name"]}
    target = vg_order(version_group)
    later = sorted((pv for pv in move.get("past_values", []) if vg_order(pv["version_group"]["name"]) >= target),
                   key=lambda pv: vg_order(pv["version_group"]["name"]))
    settled = set()
    for pv in later:
        for field in ("power", "accuracy", "pp"):
            if field not in settled and pv.get(field) is not None:
                values[field] = pv[field]
                settled.add(field)
        if "type" not in settled and pv.get("type"):
            values["type"] = pv["type"]["name"]
            settled.add("type")
    return values


def map_ailment(move_name, meta):
    raw = ((meta or {}).get("ailment") or {}).get("name", "none")
    mapped = AILMENT_MAP.get(raw, "NONE")
    if mapped == "POISON" and move_name in BADLY_POISONS:
        mapped = "BADLY_POISONED"
    return mapped, raw


def recoil_divisor(meta):
    drain = (meta or {}).get("drain") or 0
    return round(100 / -drain) if drain < 0 else 0


def build_move_row(move, version_group, target_gen):
    """Returns (row | None, exclusion_reason | None, stripped_effects)."""
    name = move["name"]
    if name in UNSUPPORTED_MOVES:
        return None, "engine does not support this mechanic yet (blocklist)", []
    meta = move.get("meta") or {}
    category = (meta.get("category") or {}).get("name", "unknown")
    if category not in INCLUDED_CATEGORIES:
        return None, f"category '{category}' not supported", []
    generation = gen_number(move["generation"]["name"])
    if generation > target_gen:
        return None, f"introduced in generation {generation}", []

    values = resolve_move_values(move, version_group)
    move_type = values["type"].upper()
    if move_type not in valid_types(target_gen):
        return None, f"type {move_type} does not exist in generation {target_gen}", []
    if not values["pp"]:
        return None, "no PP value", []

    ailment, raw_ailment = map_ailment(name, meta)
    stripped = []
    if move["damage_class"]["name"] == "status":
        if ailment == "NONE":
            return None, f"status move with unsupported effect ({raw_ailment})", []
        power, chance = 0, 100
    else:
        if not values["power"]:
            return None, "no fixed base power", []
        power = values["power"]
        chance = meta.get("ailment_chance") or 0
        if ailment == "NONE":
            chance = 0
            if raw_ailment != "none":
                stripped.append(f"ailment '{raw_ailment}'")
        elif chance == 0:
            chance = 100
        if category in ("damage-lower", "damage-raise"):
            stripped.append("stat change")
        if meta.get("flinch_chance"):
            stripped.append("flinch")
        if meta.get("crit_rate"):
            stripped.append("high crit rate")

    row = {"id": move["id"], "name": name, "generation": generation, "type": move_type, "power": power,
           "accuracy": values["accuracy"], "pp": values["pp"], "priority": move.get("priority", 0),
           "ailment": ailment, "ailment_chance": chance, "recoil_divisor": recoil_divisor(meta)}
    return row, None, stripped


def build_species_row(pokemon, target_gen):
    types = resolve_types(pokemon, target_gen)
    stats = resolve_base_stats(pokemon)
    return {"id": pokemon["id"], "dex_number": pokemon["id"], "name": pokemon["name"],
            "generation": generation_of_dex(pokemon["id"]), "primary_type": types[0],
            "secondary_type": types[1] if len(types) > 1 else None,
            "base_hp": stats["hp"], "base_attack": stats["attack"], "base_defense": stats["defense"],
            "base_sp_attack": stats["sp_attack"], "base_sp_defense": stats["sp_defense"],
            "base_speed": stats["speed"]}


def sql_value(v):
    if v is None:
        return "NULL"
    if isinstance(v, int):
        return str(v)
    return "'" + str(v).replace("'", "''") + "'"


def insert_statement(table, columns, rows):
    lines = [f"INSERT INTO {table} ({', '.join(columns)}) VALUES"]
    lines.append(",\n".join("  (" + ", ".join(sql_value(r[c]) for c in columns) + ")" for r in rows) + ";")
    return "\n".join(lines)


SPECIES_COLS = ["id", "dex_number", "name", "generation", "primary_type", "secondary_type", "base_hp",
                "base_attack", "base_defense", "base_sp_attack", "base_sp_defense", "base_speed"]
MOVE_COLS = ["id", "name", "generation", "type", "power", "accuracy", "pp", "priority", "ailment",
             "ailment_chance", "recoil_divisor"]


def generate_sql(species_rows, move_rows, learn_rows, version_group, generated_on):
    parts = [f"-- GENERATED by tools/generate_seed.py on {generated_on} from PokeAPI. Do not edit by hand.",
             f"-- Ruleset-era data for '{version_group}'. Regenerate with the script, do not patch this file.",
             "",
             insert_statement("pokemon_species", SPECIES_COLS, species_rows), "",
             insert_statement("moves", MOVE_COLS, move_rows), "",
             insert_statement("learnable_move", ["species_id", "move_id", "version_group"], learn_rows), "",
             "-- Explicit ids were inserted, so move the serial counters past them.",
             "SELECT setval(pg_get_serial_sequence('pokemon_species', 'id'), (SELECT MAX(id) FROM pokemon_species));",
             "SELECT setval(pg_get_serial_sequence('moves', 'id'), (SELECT MAX(id) FROM moves));", ""]
    return "\n".join(parts)


# ----------------------------------------------------------------------------- network + orchestration

def fetch(path):
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    cache_file = CACHE_DIR / (path.strip("/").replace("/", "_") + ".json")
    if cache_file.exists():
        return json.loads(cache_file.read_text(encoding="utf-8"))
    url = f"{API}/{path.strip('/')}"
    last_error = None
    for attempt in range(4):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "pokesim-seed-generator (portfolio project)"})
            with urllib.request.urlopen(request, timeout=30) as response:
                data = json.loads(response.read().decode("utf-8"))
            cache_file.write_text(json.dumps(data), encoding="utf-8")
            time.sleep(0.1)
            return data
        except (urllib.error.URLError, TimeoutError) as error:
            last_error = error
            time.sleep(2 ** attempt)
    raise RuntimeError(f"Could not fetch {url}: {last_error}")


def build_dataset(fetch_fn, dex_max, version_group, target_gen):
    pokemon_by_id = {}
    for dex in range(1, dex_max + 1):
        pokemon_by_id[dex] = fetch_fn(f"pokemon/{dex}")
        if dex % 25 == 0:
            print(f"  fetched {dex}/{dex_max} species", file=sys.stderr)

    learnsets = {dex: learnable_moves(p, version_group) for dex, p in pokemon_by_id.items()}
    all_move_names = sorted(set().union(*learnsets.values()))
    print(f"  {len(all_move_names)} distinct moves to resolve", file=sys.stderr)

    move_rows, excluded, stripped_log = {}, {}, {}
    for i, name in enumerate(all_move_names, 1):
        row, reason, stripped = build_move_row(fetch_fn(f"move/{name}"), version_group, target_gen)
        if row:
            move_rows[name] = row
            if stripped:
                stripped_log[name] = stripped
        else:
            excluded[name] = reason
        if i % 50 == 0:
            print(f"  resolved {i}/{len(all_move_names)} moves", file=sys.stderr)

    species_rows, learn_rows, no_moves, past_type_notes = [], [], [], []
    for dex, pokemon in pokemon_by_id.items():
        kept = sorted(move_rows[m]["id"] for m in learnsets[dex] if m in move_rows)
        if not kept:
            no_moves.append(pokemon["name"])
            continue
        row = build_species_row(pokemon, target_gen)
        current = [t["type"]["name"].upper() for t in sorted(pokemon["types"], key=lambda t: t["slot"])]
        if [row["primary_type"]] + ([row["secondary_type"]] if row["secondary_type"] else []) != current:
            past_type_notes.append(f"{pokemon['name']}: {current} -> {row['primary_type']}/{row['secondary_type']}")
        species_rows.append(row)
        learn_rows.extend({"species_id": dex, "move_id": mid, "version_group": version_group} for mid in kept)

    used_ids = {r["move_id"] for r in learn_rows}
    final_moves = sorted((r for r in move_rows.values() if r["id"] in used_ids), key=lambda r: r["id"])
    report = {"species": len(species_rows), "moves": len(final_moves), "learnable": len(learn_rows),
              "excluded": excluded, "stripped": stripped_log, "no_moves": no_moves, "past_types": past_type_notes}
    return species_rows, final_moves, learn_rows, report


def format_report(report):
    lines = [f"Species seeded: {report['species']}", f"Moves seeded: {report['moves']}",
             f"Learnset rows: {report['learnable']}", "",
             f"Species excluded (no supported moves): {report['no_moves'] or 'none'}", "",
             "Species whose types were rewound to the target generation:", *[f"  {n}" for n in report["past_types"]],
             "", "Moves seeded with secondary effects STRIPPED (engine cannot model them yet):",
             *[f"  {m}: {', '.join(e)}" for m, e in sorted(report["stripped"].items())],
             "", "Moves excluded:", *[f"  {m}: {r}" for m, r in sorted(report["excluded"].items())]]
    return "\n".join(lines) + "\n"


def sanity_warnings(report):
    """A silent over-filter once cost us most of the moves; make that loud next time."""
    warnings = []
    species = max(report["species"], 1)
    if report["learnable"] / species < 20:
        warnings.append(f"only {report['learnable'] / species:.0f} moves per species on average (expected ~25-40). "
                        "Check the exclusion reasons in the report: a category name may not match the API.")
    if report["moves"] < 100:
        warnings.append(f"only {report['moves']} moves seeded (expected 120+).")
    return warnings


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dex-max", type=int, default=151)
    parser.add_argument("--version-group", default="firered-leafgreen")
    parser.add_argument("--target-gen", type=int, default=3)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    args = parser.parse_args()

    print("Fetching from PokeAPI (cached in tools/.cache)...", file=sys.stderr)
    species, moves, learn, report = build_dataset(fetch, args.dex_max, args.version_group, args.target_gen)
    args.out.write_text(generate_sql(species, moves, learn, args.version_group, datetime.date.today()), encoding="utf-8")
    args.report.write_text(format_report(report), encoding="utf-8")
    print(f"Wrote {args.out}\nWrote {args.report}\n"
          f"{report['species']} species, {report['moves']} moves, {report['learnable']} learnset rows", file=sys.stderr)
    for warning in sanity_warnings(report):
        print(f"WARNING: {warning}", file=sys.stderr)


if __name__ == "__main__":
    main()
