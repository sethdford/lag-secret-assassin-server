DROP TABLE IF EXISTS Players;
DROP TABLE IF EXISTS Kills;

CREATE TABLE Players (
  PlayerID    TEXT,
  Name        TEXT,
  TargetID    TEXT,
  Secret      TEXT,
  Alive       BOOLEAN,
  LastWill    TEXT,
  FOREIGN KEY (TargetID) REFERENCES Players(PlayerID),
  PRIMARY KEY (PlayerID)
);

CREATE TABLE Kills (
  KillerID    TEXT,
  VictimID    TEXT,
  Time        DATE,
  Latitude    REAL,
  Longitude   REAL,
  FOREIGN KEY (KillerID) REFERENCES Players(PlayerID),
  FOREIGN KEY (VictimID) REFERENCES Players(PlayerID),
  PRIMARY KEY (KillerID, VictimID)
);

