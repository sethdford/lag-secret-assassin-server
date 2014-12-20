from email.mime.text import MIMEText
from subprocess import Popen, PIPE

# sendmail on Stanford clusters will only deliver to @stanford.edu addresses
sendmail = '/usr/sbin/sendmail'

def send_email(receivers, subject, message):
  if not isinstance(receivers, list):
    receivers = [receivers]
  email = MIMEText(message)
  email['From'] = 'MI6 <m@sis.gov.uk>'
  email['To'] = ', '.join(receivers)
  email['Subject'] = subject
  email['Reply-To'] = 'Raven <jcx@stanford.edu>'
  p = Popen([sendmail, '-ti'], stdin=PIPE)
  p.communicate(email.as_string())
  return p.returncode

