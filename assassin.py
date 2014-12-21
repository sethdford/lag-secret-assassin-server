#!/usr/bin/env python
import os
import sqlite3
import sys
sys.path.insert(0, 'lib')

from flask import Flask, render_template, redirect, url_for, flash, request

from datetime import datetime
from email.mime.text import MIMEText
from subprocess import Popen, PIPE

# ============================================================================

sqlite3.register_converter('BOOLEAN', lambda x: x == 'True')
sqlite3.register_converter('DATE', lambda x: datetime.strptime(x, "%Y-%m-%dT%H:%M:%S.%f"))
sqlite3.register_adapter(bool, lambda x: str(x))
sqlite3.register_adapter(datetime, lambda x: x.isoformat())

# sendmail on Stanford clusters will only deliver to @stanford.edu addresses
sendmail = '/usr/sbin/sendmail'

app = Flask(__name__)

# Retrieves the ID from WebAuth, or defaults to dev_user if running locally
sunetid = os.getenv('WEBAUTH_USER', 'dev_user')
dev_mode = sunetid == 'dev_user'

# ============================================================================
# Context Processors
# ============================================================================

@app.context_processor
def inject_user():
  return dict(id=sunetid, player=None)

@app.context_processor
def utility_processor():
  def print_alive(num_alive):
    if num_alive > 1:
      return "<strong>%i</strong> players are alive." % num_alive
    else:
      return "Only the winner remains."
  def row_class(alive):
    if alive:
      return 'default'
    else:
      return 'danger'
  def print_time(dt):
    return dt.strftime('%a, %b %d at %I:%M:%S %p')
  return dict(len=len,
              enumerate=enumerate,
              print_alive=print_alive,
              row_class=row_class,
              print_time=print_time)

# ============================================================================
# Routes
# ============================================================================

@app.route('/')
def home():
  conn = connect_db()
  cursor = conn.cursor()
  cursor.execute("""
    SELECT p1.PlayerID, p1.TargetID, p1.Alive, p2.Secret TargetSecret, p2.Name TargetName
    FROM Players p1 JOIN Players p2
    ON p1.TargetID = p2.PlayerID
    WHERE p1.PlayerID = ?""", (sunetid,))
  player = cursor.fetchone()
  cursor.execute("SELECT COUNT(*) FROM Players WHERE Alive = 'True'")
  num_alive = cursor.fetchone()[0]

  kills = None
  if player != None:
    cursor.execute("""
      SELECT Players.Name, Kills.Time
      FROM Kills
      JOIN Players
      ON VictimID = PlayerID
      WHERE KillerID = ? ORDER BY Time DESC""", (sunetid,))
    kills = cursor.fetchall()

  return render_template('home.html', player=player, num_alive=num_alive, kills=kills)

@app.route('/die', methods=['POST'])
def die():
  conn = connect_db()
  cursor = conn.cursor()
  cursor.execute("""
    SELECT killer.PlayerID, dead.TargetID, next.Name, next.Secret
    FROM Players killer
    JOIN Players dead
    ON dead.PlayerID = killer.TargetID
    JOIN Players next
    ON dead.TargetID = next.PlayerID
    WHERE dead.PlayerID = ? AND dead.Alive = 'True'""", (sunetid,))
  row = cursor.fetchone()

  killer_id = row['PlayerID']
  target_id = row['TargetID']
  latitude = float(request.form['latitude']) if request.form['latitude'] != '' else -10000
  longitude = float(request.form['longitude']) if request.form['longitude'] != '' else -10000

  cursor.execute("UPDATE Players SET Alive = 'False' WHERE PlayerID = ?", (sunetid,))
  cursor.execute("UPDATE Players SET TargetID = ? WHERE PlayerID = ?", (target_id, killer_id))
  cursor.execute("INSERT INTO Kills VALUES (?, ?, ?, ?, ?)",
    (killer_id, sunetid, datetime.now(), latitude, longitude))
  conn.commit()

  if killer_id == target_id:
    notify_victory(killer_id)
  else:
    notify_death(killer_id, row['Name'], row['Secret'])

  return redirect(url_for('home'))

@app.route('/stats')
def stats():
  conn = connect_db()
  cursor = conn.cursor()
  top_limit = 5
  cursor.execute("""
    SELECT Players.Name, Players.Alive, Count(*) Count
    FROM Kills
    JOIN Players
    ON Kills.KillerID = Players.PlayerID
    GROUP BY Kills.KillerID
    ORDER BY Count DESC
    LIMIT ?""", (top_limit,))
  top_scores = cursor.fetchall()

  cursor.execute("""
    SELECT Players.Name, Kills.Time
    FROM Kills
    JOIN Players
    ON Kills.VictimID = Players.PlayerID
    ORDER BY Time DESC
    LIMIT ?""", (top_limit,))
  recent_deaths = cursor.fetchall()

  cursor.execute("SELECT COUNT(*) FROM Players WHERE Alive = 'True'")
  num_alive = cursor.fetchone()[0]

  cursor.execute("SELECT COUNT(*) FROM Players WHERE Alive = 'False'")
  num_dead = cursor.fetchone()[0]

  return render_template('stats.html',
                         top_scores=top_scores,
                         recent_deaths=recent_deaths,
                         num_alive=num_alive,
                         num_dead=num_dead)

# ============================================================================
# Utility Functions
# ============================================================================

def notify_victory(victor_id):
  send_email([victor_id + '@stanford.edu'],
             'Congratulations!',
             render_template('victory.html'))

def notify_death(killer_id, victim, secret):
  send_email([killer_id + '@stanford.edu'],
             'Target Successfully Eliminated',
             render_template('notify.html', victim=victim, secret=secret))

def send_email(receivers, subject, message):
  if not isinstance(receivers, list):
    receivers = [receivers]
  email = MIMEText(message, 'html')
  email['From'] = 'MI6 <m@sis.gov.uk>'
  email['To'] = ', '.join(receivers)
  email['Subject'] = subject
  email['Reply-To'] = 'Raven <jcx@stanford.edu>'

  if dev_mode:
    print email.as_string()
  else:
    p = Popen([sendmail, '-ti'], stdin=PIPE)
    p.communicate(email.as_string())
    return p.returncode

def connect_db():
  conn = sqlite3.connect('db/game.db', detect_types=sqlite3.PARSE_DECLTYPES)
  conn.row_factory = sqlite3.Row
  return conn

# ============================================================================

if __name__ == '__main__':
  app.run(debug=True)
