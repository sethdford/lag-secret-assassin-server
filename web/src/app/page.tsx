'use client';

import { useEffect, useState, useCallback } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import Alert from '@/components/ui/Alert';
import Card from '@/components/ui/Card';
import Button from '@/components/ui/Button';
import { DeathConfirmationModal } from '@/components/ui';
import { Player } from '@/models/Player';
import { Kill } from '@/models/Kill';
import { PlayerApi } from '@/api';
import Link from 'next/link';

export default function Home() {
  const { isAuthenticated, player: authPlayer } = useAuth();
  const [player, setPlayer] = useState<Player | null>(null);
  const [kills, setKills] = useState<Kill[]>([]);
  const [numAlive, setNumAlive] = useState<number>(0);
  const [victorName, setVictorName] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [isDeathModalOpen, setIsDeathModalOpen] = useState<boolean>(false);

  const fetchPlayerData = useCallback(async () => {
    if (!authPlayer?.playerID) return;
    
    setIsLoading(true);
    try {
      const playerData = await PlayerApi.getPlayerById(authPlayer.playerID);
      setPlayer(playerData);
      
      setKills([]);
      
      setNumAlive(5); 
      if (playerData && playerData.playerID === playerData.targetID) {
        setVictorName(playerData.playerName);
      }
    } catch (err) {
      const apiError = err as { message?: string };
      setError('Failed to load player data.');
      console.error(apiError.message || err);
    } finally {
      setIsLoading(false);
    }
  }, [authPlayer?.playerID]);

  useEffect(() => {
    if (isAuthenticated && authPlayer) {
      fetchPlayerData();
    } else {
      setIsLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated, authPlayer]); // Keep dependency array simple

  const handleDeathConfirmed = () => {
    setIsDeathModalOpen(false);
    // Refresh player data after death confirmation
    fetchPlayerData();
  };

  if (isLoading) {
    return <div className="text-center py-8">Loading player data...</div>;
  }

  if (!isAuthenticated || !authPlayer) {
    return (
      <div className="text-center py-8">
        <h1 className="text-2xl font-bold mb-4">Welcome to Assassin!</h1>
        <p className="mb-4">Please sign in to view your game status.</p>
        <Link href="/auth/signin">
          <Button>Sign In</Button>
        </Link>
      </div>
    );
  }

  if (error) {
     return <Alert type="error">{error}</Alert>;
  }

  return (
    <div className="space-y-6 p-4 md:p-6">
      <Alert type="info">
        You are logged in as <strong>{authPlayer.playerName || authPlayer.playerID}</strong>.
      </Alert>

      {numAlive > 0 && (
        <Alert type={numAlive === 1 ? 'success' : 'warning'}>
          {numAlive === 1
            ? `Game over! ${victorName} is the winner!`
            : `${numAlive} players still alive.`
          }
        </Alert>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div>
          {player === null ? (
            <Alert type="warning">You are not currently in an active game.</Alert>
          ) : !player.status || player.status.toUpperCase() === 'DEAD' ? (
            <Alert type="error">You have been eliminated.</Alert>
          ) : player.playerID === player.targetID ? (
            <Alert type="success">You are the winner!</Alert>
          ) : numAlive > 1 ? (
            <>
              <Card 
                title="Next Target" 
                className="mb-6"
              >
                <div className="space-y-2">
                  <div className="flex justify-between">
                    <span className="font-medium">Name:</span>
                    <span>{player.targetName || 'Target not assigned'}</span>
                  </div>
                  {player.targetSecret && (
                    <div className="flex justify-between">
                      <span className="font-medium">Secret:</span>
                      <span>{player.targetSecret}</span>
                    </div>
                  )}
                </div>
              </Card>

              <div className="text-center my-6">
                <Button 
                  variant="danger"
                  onClick={() => setIsDeathModalOpen(true)}
                >
                  Report My Elimination
                </Button>
              </div>
            </>
          ) : null}
        </div>

        {kills && kills.length > 0 && (
          <div>
            <Card title="Your Kills">
              <div className="divide-y divide-slate-200 dark:divide-slate-700">
                {kills.map((kill) => (
                  <div key={`${kill.victimID}-${kill.time}`} className="py-3">
                    <div className="flex justify-between items-start">
                      <div className="font-medium">{kill.victimName || 'Unknown Player'}</div>
                      <div className="text-xs text-slate-500 dark:text-slate-400">
                        {new Date(kill.time).toLocaleString()}
                      </div>
                    </div>
                    {kill.lastWill && (
                      <div className="mt-2 text-sm italic">
                        &ldquo;{kill.lastWill}&rdquo;
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </Card>
          </div>
        )}
      </div>

      {/* Death Confirmation Modal */}
      {player && authPlayer && (
        <DeathConfirmationModal
          isOpen={isDeathModalOpen}
          onClose={() => setIsDeathModalOpen(false)}
          onConfirm={handleDeathConfirmed}
          playerId={authPlayer.playerID}
        />
      )}
    </div>
  );
}
