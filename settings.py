NORMAL_ASSASSIN = 'Regular'
WORD_ASSASSIN = 'Word'

# ============================================================================
# Change these options accordingly
# ============================================================================

# Set this before running ./new_game.sh
game_mode = WORD_ASSASSIN

# Player IDs of gamemasters allowed to access to /admin
admins = ['jcx', 'dev_user', 'justinez']

# Sendmail on Stanford clusters will only deliver to @stanford.edu addresses
sendmail = '/usr/sbin/sendmail'
email_sender = 'MI6 <m@sis.gov.uk>'
email_reply_to = 'Raven <jcx@stanford.edu>'

# Number of rows to display on /stats
stats_row_limit = 10


# ============================================================================
# These options do not usually need to change
# ============================================================================

# Player ID for dev mode when working outside of WebAuth
dev_id = 'dev_user'

# Path to generated game database
db_path = 'db/game.db'

# Filename of cgi executable
cgi_filename = 'app.cgi'