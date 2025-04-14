export interface Kill {
  killerID: string;
  time: string;
  victimID: string;
  latitude?: number;
  longitude?: number;
  killerName?: string;
  victimName?: string;
  lastWill?: string;
}

export interface ReportKillRequest {
  victimID: string;
  secret: string;
  latitude?: number;
  longitude?: number;
  lastWill?: string;
}

export interface ConfirmDeathRequest {
  playerId: string;
  lastWill?: string;
} 