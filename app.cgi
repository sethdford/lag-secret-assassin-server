#!/usr/bin/env python
from wsgiref.handlers import CGIHandler
from assassin import app

CGIHandler().run(app)
