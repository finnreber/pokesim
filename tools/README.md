# tools/

`generate_seed.py` builds `src/main/resources/db/migration/V2__seed_gen1_firered.sql` from PokeAPI.
Run it from the repo root with Python 3.9+ (standard library only, no pip install needed):

    python tools/generate_seed.py

Raw API responses are cached in `tools/.cache/` (git-ignored), so reruns are offline.
It also writes `tools/seed_report.txt`: which moves were excluded or had effects stripped, and why.
Tests: `python -m unittest discover -s tools -p "test_*.py"`.
