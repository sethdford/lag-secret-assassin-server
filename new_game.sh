#!/usr/bin/env bash
python scripts/parse.py db/
sqlite3 db/game.db < scripts/create.sql
sqlite3 db/game.db < scripts/load.txt
rm db/players.dat

