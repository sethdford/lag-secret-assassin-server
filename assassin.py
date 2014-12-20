#!/usr/bin/env python
import os
import sqlite3
import sys
sys.path.insert(0, 'lib')

from flask import Flask
from util import mailer

app = Flask(__name__)
conn = sqlite3.connect('db/game.db')
user_id = os.environ['WEBAUTH_USER']

@app.route('/')
def hello_world():
  return 'Hi'

if __name__ == '__main__':
  app.run()

