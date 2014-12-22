#!/usr/bin/env bash
python parse_data.py db/
sqlite3 db/game.db < db/create.sql
sqlite3 db/game.db < db/load.txt
rm db/players.dat
