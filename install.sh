#!/usr/bin/env bash
printf "Installing python dependencies to ./lib... "
rm -rf lib
pip install --target=lib --ignore-installed -r requirements.txt > /dev/null
printf "Done.\n"

# Create sample player data
if [ ! -f db/names ]
then
  printf "Populating sample player data... "
  printf "dev_user, Dev\n" >> db/names
  printf "jcx, Raven\n" >> db/names
  printf "Done.\n"
fi

# Create sample word data
if [ ! -f db/words ]
then
  printf "Populating sample word data... "
  printf "maple\ndestiny\nmoot\nclementine\nclandestine\n" >> db/words
  printf "Done.\n"
fi