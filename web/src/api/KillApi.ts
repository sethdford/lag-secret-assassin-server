import { api } from '@/api/apiClient';
import { Kill, ReportKillRequest, ConfirmDeathRequest } from '@/models/Kill';

// Environment flag to disable auth for local development
const DISABLE_AUTH = process.env.NEXT_PUBLIC_DISABLE_AUTH_FOR_LOCAL === 'true';

export const KillApi = {
  /**
   * Fetch all kills
   */
  async getAllKills(): Promise<Kill[]> {
    try {
      const response = await api.get('/kills');
      return response.data;
    } catch (error) {
      console.error('Error fetching all kills:', error);
      throw error;
    }
  },

  /**
   * Fetch kills by killer ID
   */
  async getKillsByKillerId(killerId: string): Promise<Kill[]> {
    try {
      const response = await api.get(`/kills/killer/${killerId}`);
      return response.data;
    } catch (error) {
      console.error(`Error fetching kills for killer ${killerId}:`, error);
      throw error;
    }
  },

  /**
   * Fetch kills by victim ID
   */
  async getKillsByVictimId(victimId: string): Promise<Kill[]> {
    try {
      const response = await api.get(`/kills/victim/${victimId}`);
      return response.data;
    } catch (error) {
      console.error(`Error fetching kills for victim ${victimId}:`, error);
      throw error;
    }
  },

  /**
   * Report a new kill
   */
  async reportKill(killData: ReportKillRequest): Promise<Kill> {
    try {
      const response = await api.post('/kills', killData);
      return response.data;
    } catch (error) {
      console.error('Error reporting kill:', error);
      throw error;
    }
  },

  /**
   * Confirm own death and submit last will
   */
  async confirmDeath(data: ConfirmDeathRequest): Promise<void> {
    try {
      await api.post('/kills/confirm-death', data);
    } catch (error) {
      console.error('Error confirming death:', error);
      throw error;
    }
  },

  /**
   * Delete a kill by ID
   */
  async deleteKill(killerId: string, time: string): Promise<void> {
    try {
      await api.delete(`/kills/${killerId}/${time}`);
    } catch (error) {
      console.error(`Error deleting kill ${killerId}/${time}:`, error);
      throw error;
    }
  },

  /**
   * Fetch recent kills with an optional limit
   */
  async getRecentKills(limit: number = 10): Promise<Kill[]> {
    if (DISABLE_AUTH) {
      console.log('Using mock recent kills data for development');
      // Generate some mock kill data
      const mockKills: Kill[] = [];
      const now = new Date();
      
      for (let i = 0; i < limit; i++) {
        const killDate = new Date(now);
        killDate.setHours(now.getHours() - i * 2); // Each kill 2 hours apart
        
        mockKills.push({
          killerID: `player-${i % 3 + 1}`,
          killerName: `Player ${i % 3 + 1}`,
          victimID: `player-${(i + 1) % 3 + 1}`,
          victimName: `Player ${(i + 1) % 3 + 1}`,
          time: killDate.toISOString(),
          latitude: Math.random() * 180 - 90,
          longitude: Math.random() * 360 - 180
        });
      }
      
      return mockKills;
    }
    
    try {
      const response = await api.get(`/kills/recent?limit=${limit}`);
      return response.data;
    } catch (error) {
      console.error(`Error fetching recent kills:`, error);
      throw error;
    }
  }
}; 