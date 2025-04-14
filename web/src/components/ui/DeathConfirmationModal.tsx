'use client';

import { useState } from 'react';
import { KillApi } from '@/api';
import Button from './Button';

interface DeathConfirmationModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  playerId: string;
}

export default function DeathConfirmationModal({
  isOpen,
  onClose,
  onConfirm,
  playerId
}: DeathConfirmationModalProps) {
  const [lastWill, setLastWill] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    setError(null);

    try {
      // Call API to confirm death and submit last will
      await KillApi.confirmDeath({
        playerId,
        lastWill
      });
      onConfirm();
    } catch (err) {
      const apiError = err as { message?: string };
      setError(apiError.message || 'Failed to confirm death. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
      <div className="bg-white dark:bg-slate-800 rounded-lg shadow-xl max-w-md w-full overflow-hidden transform transition-all">
        <div className="bg-red-600 p-4">
          <h2 className="text-xl font-bold text-white text-center">Confirm Your Death</h2>
        </div>
        
        <form onSubmit={handleSubmit} className="p-6">
          {error && (
            <div className="mb-4 p-3 bg-red-100 border border-red-400 text-red-700 rounded-md">
              {error}
            </div>
          )}
          
          <p className="mb-4 text-center text-slate-600 dark:text-slate-300">
            You are about to confirm your elimination from the game. This action cannot be undone.
          </p>
          
          <div className="mb-6">
            <label htmlFor="lastWill" className="block text-sm font-medium mb-2">
              Your Last Will & Testament
            </label>
            <textarea
              id="lastWill"
              value={lastWill}
              onChange={(e) => setLastWill(e.target.value)}
              rows={4}
              className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-red-500 focus:border-transparent bg-white dark:bg-slate-700 text-slate-900 dark:text-white"
              placeholder="Share your final thoughts with the world..."
            />
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
              Optional: Leave a message for other players to see
            </p>
          </div>
          
          <div className="flex justify-between gap-4">
            <Button
              type="button"
              variant="secondary"
              onClick={onClose}
              className="w-1/2"
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant="danger"
              disabled={isSubmitting}
              className="w-1/2"
            >
              {isSubmitting ? 'Confirming...' : 'Confirm Death'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
} 