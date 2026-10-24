"""
Tests for the seed generator. Fixtures are small hand-made dicts shaped like PokeAPI responses,
so the rewind-to-Gen-3 logic is checked without the network. The end-to-end test loads the generated
SQL into SQLite against the real V1 schema, so a malformed INSERT fails here and not on deploy.
"""
import sqlite3
import unittest
from pathlib import Path

import generate_seed as g

V1_SCHEMA = Path(__file__).resolve().parent.parent / "src/main/resources/db/migration/V1__reference_data.sql"
FRLG = "firered-leafgreen"


def named(name):
    return {"name": name}


def type_slots(*names):
    return [{"slot": i + 1, "type": named(n)} for i, n in enumerate(names)]


def pokemon(pid, name, types, moves, past_types=None):
    stats = [{"base_stat": 50, "stat": named(s)} for s in
             ("hp", "attack", "defense", "special-attack", "special-defense", "speed")]
    return {"id": pid, "name": name, "types": type_slots(*types), "stats": stats,
            "past_types": past_types or [],
            "moves": [{"move": named(m), "version_group_details": [
                {"version_group": named(FRLG), "move_learn_method": named(method)}]} for m, method in moves]}


def move(mid, name, mtype, power, accuracy, pp, damage_class, category, ailment="none", chance=0,
         drain=0, generation="generation-i", past_values=None, flinch=0, priority=0):
    return {"id": mid, "name": name, "type": named(mtype), "power": power, "accuracy": accuracy, "pp": pp,
            "priority": priority, "damage_class": named(damage_class), "generation": named(generation),
            "past_values": past_values or [],
            "meta": {"category": named(category), "ailment": named(ailment), "ailment_chance": chance,
                     "drain": drain, "flinch_chance": flinch, "crit_rate": 0}}


class SpeciesRewindTest(unittest.TestCase):
    def test_current_types_used_when_nothing_changed(self):
        p = pokemon(25, "pikachu", ["electric"], [])
        self.assertEqual(["ELECTRIC"], g.resolve_types(p, 3))

    def test_fairy_change_after_target_gen_is_rewound(self):
        # Clefairy was Normal through Gen 5, Fairy afterwards.
        p = pokemon(35, "clefairy", ["fairy"], [],
                    past_types=[{"generation": named("generation-v"), "types": type_slots("normal")}])
        self.assertEqual(["NORMAL"], g.resolve_types(p, 3))

    def test_change_before_target_gen_is_ignored(self):
        # Magnemite became Electric/Steel in Gen 2, so Gen 3 already has Steel.
        p = pokemon(81, "magnemite", ["electric", "steel"], [],
                    past_types=[{"generation": named("generation-i"), "types": type_slots("electric")}])
        self.assertEqual(["ELECTRIC", "STEEL"], g.resolve_types(p, 3))

    def test_slot_order_is_preserved(self):
        p = pokemon(6, "charizard", ["fire", "flying"], [])
        p["types"].reverse()   # API order must not matter, slot does
        self.assertEqual(["FIRE", "FLYING"], g.resolve_types(p, 3))


class LearnsetTest(unittest.TestCase):
    def test_only_target_game_and_allowed_methods(self):
        p = pokemon(1, "a", ["normal"], [("tackle", "level-up"), ("toxic", "machine"), ("charm", "egg")])
        p["moves"].append({"move": named("surf"), "version_group_details": [
            {"version_group": named("red-blue"), "move_learn_method": named("machine")}]})
        self.assertEqual({"tackle", "toxic"}, g.learnable_moves(p, FRLG))


class MoveRewindTest(unittest.TestCase):
    def test_thunderbolt_gets_its_gen3_power(self):
        tb = move(85, "thunderbolt", "electric", 90, 100, 15, "special", "damage-ailment", "paralysis", 10,
                  past_values=[{"power": 95, "accuracy": None, "pp": None, "type": None,
                                "version_group": named("black-2-white-2")}])
        self.assertEqual(95, g.resolve_move_values(tb, FRLG)["power"])

    def test_changes_before_target_do_not_apply(self):
        m = move(1, "m", "normal", 90, 100, 15, "physical", "damage",
                 past_values=[{"power": 40, "accuracy": None, "pp": None, "type": None,
                               "version_group": named("red-blue")}])
        self.assertEqual(90, g.resolve_move_values(m, FRLG)["power"])

    def test_earliest_later_entry_wins_per_field(self):
        m = move(1, "m", "normal", 100, 100, 10, "physical", "damage", past_values=[
            {"power": 70, "accuracy": None, "pp": None, "type": None, "version_group": named("x-y")},
            {"power": 50, "accuracy": 95, "pp": None, "type": None, "version_group": named("black-white")}])
        values = g.resolve_move_values(m, FRLG)
        self.assertEqual(50, values["power"])      # black-white is earlier than x-y
        self.assertEqual(95, values["accuracy"])

    def test_type_is_rewound(self):
        m = move(1, "m", "fairy", 40, 100, 10, "special", "damage",
                 past_values=[{"power": None, "accuracy": None, "pp": None, "type": named("normal"),
                               "version_group": named("black-2-white-2")}])
        self.assertEqual("normal", g.resolve_move_values(m, FRLG)["type"])


class MoveRowTest(unittest.TestCase):
    def row(self, m):
        return g.build_move_row(m, FRLG, 3)

    def test_secondary_status_effect_kept(self):
        r, reason, stripped = self.row(move(85, "thunderbolt", "electric", 95, 100, 15, "special",
                                            "damage-ailment", "paralysis", 10))
        self.assertIsNone(reason)
        self.assertEqual(("PARALYSIS", 10), (r["ailment"], r["ailment_chance"]))
        self.assertEqual([], stripped)

    def test_toxic_is_badly_poisoned_not_plain_poison(self):
        r, _, _ = self.row(move(92, "toxic", "poison", None, 90, 10, "status", "ailment", "poison"))
        self.assertEqual(("BADLY_POISONED", 0, 100), (r["ailment"], r["power"], r["ailment_chance"]))

    def test_unsupported_ailment_on_status_move_is_excluded(self):
        r, reason, _ = self.row(move(48, "supersonic", "normal", None, 55, 20, "status", "ailment", "confusion"))
        self.assertIsNone(r)
        self.assertIn("unsupported effect", reason)

    def test_unsupported_secondary_ailment_is_stripped_and_reported(self):
        r, _, stripped = self.row(move(83, "fire-spin", "fire", 15, 70, 15, "special", "damage-ailment", "trap", 100))
        self.assertEqual("NONE", r["ailment"])
        self.assertIn("ailment 'trap'", stripped)

    def test_stat_change_and_flinch_are_reported(self):
        r, _, stripped = self.row(move(94, "psychic", "psychic", 90, 100, 10, "special", "damage-lower", flinch=10))
        self.assertIsNotNone(r)
        self.assertEqual(["stat change", "flinch"], stripped)

    def test_recoil_divisor_from_drain(self):
        r, _, _ = self.row(move(38, "double-edge", "normal", 120, 100, 15, "physical", "damage", drain=-33))
        self.assertEqual(3, r["recoil_divisor"])
        r, _, _ = self.row(move(36, "take-down", "normal", 90, 85, 20, "physical", "damage", drain=-25))
        self.assertEqual(4, r["recoil_divisor"])

    def test_exclusions(self):
        cases = [move(1, "seismic-toss", "fighting", None, 100, 20, "physical", "damage"),   # no fixed power
                 move(2, "solar-beam", "grass", 120, 100, 10, "special", "damage"),          # blocklist
                 move(3, "recover", "normal", None, None, 20, "status", "heal"),             # category
                 move(4, "moonblast", "fairy", 95, 100, 15, "special", "damage", generation="generation-vi")]
        for m in cases:
            self.assertIsNone(self.row(m)[0], m["name"])

    def test_fairy_typed_move_that_was_not_rewound_is_excluded(self):
        r, reason, _ = self.row(move(5, "odd", "fairy", 40, 100, 10, "special", "damage"))
        self.assertIsNone(r)
        self.assertIn("does not exist", reason)


class EndToEndTest(unittest.TestCase):
    def setUp(self):
        mons = [pokemon(1, "pikachu", ["electric"], [("thunderbolt", "machine"), ("toxic", "machine")]),
                pokemon(2, "clefairy", ["fairy"], [("thunderbolt", "machine"), ("psychic", "machine")],
                        past_types=[{"generation": named("generation-v"), "types": type_slots("normal")}]),
                pokemon(3, "ditto-like", ["normal"], [("solar-beam", "level-up")]),   # only unsupported moves
                pokemon(4, "farfetchd", ["normal", "flying"], [("psychic", "machine")])]
        moves = [move(85, "thunderbolt", "electric", 90, 100, 15, "special", "damage-ailment", "paralysis", 10,
                      past_values=[{"power": 95, "accuracy": None, "pp": None, "type": None,
                                    "version_group": named("black-2-white-2")}]),
                 move(92, "toxic", "poison", None, 90, 10, "status", "ailment", "poison"),
                 move(94, "psychic", "psychic", 90, 100, 10, "special", "damage-lower"),
                 move(76, "solar-beam", "grass", 120, 100, 10, "special", "damage")]
        self.db = {f"pokemon/{p['id']}": p for p in mons} | {f"move/{m['name']}": m for m in moves}

    def test_dataset_and_sql_load_into_a_real_database(self):
        species, moves, learn, report = g.build_dataset(lambda path: self.db[path], 4, FRLG, 3)

        self.assertEqual(["pikachu", "clefairy", "farfetchd"], [s["name"] for s in species])
        self.assertEqual(["ditto-like"], report["no_moves"])
        self.assertEqual("NORMAL", species[1]["primary_type"])        # Clefairy rewound
        self.assertEqual(95, next(m for m in moves if m["name"] == "thunderbolt")["power"])
        self.assertNotIn("solar-beam", [m["name"] for m in moves])
        self.assertIn("psychic", report["stripped"])

        sql = g.generate_sql(species, moves, learn, FRLG, "2026-01-01")
        sqlite_sql = "\n".join(l for l in sql.splitlines() if not l.startswith("SELECT setval"))
        con = sqlite3.connect(":memory:")
        con.executescript(V1_SCHEMA.read_text(encoding="utf-8"))
        con.executescript(sqlite_sql)
        self.assertEqual(3, con.execute("SELECT COUNT(*) FROM pokemon_species").fetchone()[0])
        self.assertEqual(3, con.execute("SELECT COUNT(*) FROM moves").fetchone()[0])
        self.assertEqual(5, con.execute("SELECT COUNT(*) FROM learnable_move").fetchone()[0])

    def test_quotes_in_names_are_escaped(self):
        self.assertEqual("'farfetch''d'", g.sql_value("farfetch'd"))
        self.assertEqual("NULL", g.sql_value(None))
        self.assertEqual("42", g.sql_value(42))


class SanityWarningTest(unittest.TestCase):
    def test_overfiltering_is_flagged(self):
        report = {"species": 147, "moves": 77, "learnable": 1457}
        self.assertEqual(2, len(g.sanity_warnings(report)))

    def test_healthy_run_has_no_warnings(self):
        self.assertEqual([], g.sanity_warnings({"species": 147, "moves": 140, "learnable": 5000}))


FIXTURES = Path(__file__).resolve().parent / "fixtures"


@unittest.skipUnless((FIXTURES / "move_thunderbolt.json").exists(), "copy real responses into tools/fixtures/ first")
class RealApiContractTest(unittest.TestCase):
    """Runs against REAL PokeAPI responses (copied from tools/.cache), not our assumptions about them."""

    @staticmethod
    def load(name):
        import json
        return json.loads((FIXTURES / name).read_text(encoding="utf-8"))

    def test_thunderbolt_from_real_response(self):
        row, reason, _ = g.build_move_row(self.load("move_thunderbolt.json"), FRLG, 3)
        self.assertIsNone(reason)
        self.assertEqual(("ELECTRIC", 95, "PARALYSIS", 10), (row["type"], row["power"], row["ailment"], row["ailment_chance"]))

    def test_clefairy_from_real_response(self):
        self.assertEqual(["NORMAL"], g.resolve_types(self.load("pokemon_35.json"), 3))


if __name__ == "__main__":
    unittest.main()
