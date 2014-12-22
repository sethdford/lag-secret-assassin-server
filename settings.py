NORMAL_ASSASSIN = 'Regular'
WORD_ASSASSIN = 'Word'

# Set this before running ./new_game.sh
game_mode = WORD_ASSASSIN

# Player IDs of gamemasters allowed to access to /admin
admins = ['jcx', 'dev_user', 'justinez']

# Sendmail on Stanford clusters will only deliver to @stanford.edu addresses
sendmail = '/usr/sbin/sendmail'
email_sender = 'MI6 <m@sis.gov.uk>'
email_reply_to = 'Raven <jcx@stanford.edu>'

# Player ID for dev mode when working outside of WebAuth
dev_id = 'dev_user'

# Number of rows to display on /stats
stats_row_limit = 10

# Path to generated game database
db_path = 'db/game.db'
