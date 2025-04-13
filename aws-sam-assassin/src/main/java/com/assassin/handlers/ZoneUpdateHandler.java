package com.assassin.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.assassin.dao.GameDao;
import com.assassin.dao.GameZoneStateDao;
import com.assassin.dao.PlayerDao;
import com.assassin.dao.DynamoDbGameDao;
import com.assassin.dao.DynamoDbPlayerDao;
import com.assassin.dao.DynamoDbGameZoneStateDao;
import com.assassin.model.Game;
import com.assassin.model.Player;
import com.assassin.service.PlayerStatusService;
import com.assassin.service.ShrinkingZoneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Lambda handler triggered by a CloudWatch Scheduled Event to update game zone states
 * and apply out-of-zone damage periodically.
 */
public class ZoneUpdateHandler implements RequestHandler<ScheduledEvent, String> {

    private static final Logger logger = LoggerFactory.getLogger(ZoneUpdateHandler.class);
    private static final String SHRINKING_ZONE_CONFIG_KEY = "shrinkingZoneConfig"; // Key to identify shrinking zone games

    private final GameDao gameDao;
    private final PlayerDao playerDao;
    private final GameZoneStateDao gameZoneStateDao;
    private final ShrinkingZoneService shrinkingZoneService;
    private final PlayerStatusService playerStatusService;

    /**
     * Default constructor initializing dependencies.
     */
    public ZoneUpdateHandler() {
        // Instantiate DAOs directly
        this.gameDao = new DynamoDbGameDao();
        this.playerDao = new DynamoDbPlayerDao();
        this.gameZoneStateDao = new DynamoDbGameZoneStateDao();
        this.shrinkingZoneService = new ShrinkingZoneService(gameDao, gameZoneStateDao, playerDao);
        this.playerStatusService = new PlayerStatusService(playerDao, shrinkingZoneService, gameDao);
    }

    /**
     * Constructor for dependency injection (testing).
     */
    public ZoneUpdateHandler(GameDao gameDao, PlayerDao playerDao, GameZoneStateDao gameZoneStateDao,
                           ShrinkingZoneService shrinkingZoneService, PlayerStatusService playerStatusService) {
        this.gameDao = Objects.requireNonNull(gameDao);
        this.playerDao = Objects.requireNonNull(playerDao);
        this.gameZoneStateDao = Objects.requireNonNull(gameZoneStateDao);
        this.shrinkingZoneService = Objects.requireNonNull(shrinkingZoneService);
        this.playerStatusService = Objects.requireNonNull(playerStatusService);
    }

    @Override
    public String handleRequest(ScheduledEvent event, Context context) {
        logger.info("Received scheduled event: {}. Starting zone update process.", event.getId());
        int gamesProcessed = 0;
        int playersChecked = 0;
        int damageAppliedCount = 0;

        try {
            // 1. Find active games that use the shrinking zone
            // TODO: Need an efficient way to query for active games with SHRINKING_ZONE_CONFIG_KEY
            // For now, get all active games and filter in memory (inefficient for many games)
            List<Game> activeGames = gameDao.listGamesByStatus("ACTIVE");
            logger.info("Found {} active games to check.", activeGames.size());

            for (Game game : activeGames) {
                // Check if the game actually uses shrinking zones
                if (game.getSettings() == null || !game.getSettings().containsKey(SHRINKING_ZONE_CONFIG_KEY)) {
                    logger.debug("Skipping game {} - does not appear to be a shrinking zone game.", game.getGameID());
                    continue;
                }

                String gameId = game.getGameID();
                logger.info("Processing game: {}", gameId);
                gamesProcessed++;

                try {
                    // 2. Advance the zone state for the game
                    shrinkingZoneService.advanceZoneState(gameId);
                    logger.debug("Advanced zone state for game {}.", gameId);

                    // 3. Find active players in this game
                    // TODO: Need an efficient way to query players by gameId and status=ACTIVE
                    // Filtering in memory is inefficient.
                    List<Player> playersInGame = playerDao.getPlayersByGameId(gameId); // Assuming this exists
                    logger.debug("Found {} players potentially in game {}. Filtering for ACTIVE.", playersInGame.size(), gameId);
                    
                    for (Player player : playersInGame) {
                        if (!"ACTIVE".equalsIgnoreCase(player.getStatus())) {
                            continue; // Skip non-active players
                        }
                        
                        playersChecked++;
                        String playerId = player.getPlayerID();
                        logger.debug("Checking player {} in game {}.", playerId, gameId);

                        try {
                            // 4. Apply out-of-zone damage check
                            boolean damageCheckPerformed = playerStatusService.applyOutOfZoneDamage(playerId);
                            if (damageCheckPerformed) {
                                damageAppliedCount++;
                            }
                        } catch (Exception e) {
                            // Log error for specific player but continue processing others
                            logger.error("Error applying zone damage to player {} in game {}: {}", 
                                         playerId, gameId, e.getMessage(), e);
                        }
                    }
                } catch (Exception e) {
                    // Log error for specific game but continue processing others
                    logger.error("Error processing zone update for game {}: {}", gameId, e.getMessage(), e);
                }
            }

            String summary = String.format("Zone update complete. Processed %d games, checked %d players, damage applied/eliminated %d players.",
                                         gamesProcessed, playersChecked, damageAppliedCount);
            logger.info(summary);
            return summary;

        } catch (Exception e) {
            logger.error("Fatal error during scheduled zone update: {}", e.getMessage(), e);
            return "Error during zone update: " + e.getMessage();
        }
    }
} 