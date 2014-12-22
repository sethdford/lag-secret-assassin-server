#!/usr/bin/env bash
source new_game.sh
sqlite3 db/game.db < db/test_data.sql
