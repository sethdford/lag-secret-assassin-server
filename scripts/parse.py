import csv
import os
import sys

from random import shuffle

def readlist(path):
  f = open(path, 'r')
  l = map(lambda s : s.strip(), f.readlines())
  l = filter(lambda s : len(s) > 0, l)
  f.close()
  shuffle(l)
  return l

folder = sys.argv[1]
names = readlist(os.path.join(folder, 'names'))
words = readlist(os.path.join(folder, 'words'))

ids = map(lambda s : s.split(',')[0].strip(), names)
names = map(lambda s : s.split(',')[1].strip(), names)

# Loops the list around for target assignment
ids.append(ids[0])

with open(os.path.join(folder, 'players.dat'), 'wb') as output:
  writer = csv.writer(output, delimiter='|', quoting=csv.QUOTE_NONE)
  for i in xrange(len(names)):
    sunetid = ids[i]
    name = names[i]
    target = ids[i+1]
    word = words[i]
    writer.writerow([sunetid, name, target, word, True])

