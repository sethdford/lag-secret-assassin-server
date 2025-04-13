package com.assassin.dao;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach; // Mock static method
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assassin.exception.PlayerPersistenceException;
import com.assassin.model.Player;
import com.assassin.util.DynamoDbClientProvider;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

@ExtendWith(MockitoExtension.class)
class DynamoDbPlayerDaoTest {

    @Mock
    private DynamoDbEnhancedClient mockEnhancedClient;

    @Mock
    private DynamoDbTable<Player> mockPlayerTable;

    private DynamoDbPlayerDao playerDao;

    private MockedStatic<DynamoDbClientProvider> mockClientProvider;
    private MockedStatic<TableSchema> mockTableSchema;

    @BeforeEach
    void setUp() {
        // Mock the static method call to get the enhanced client
        mockClientProvider = Mockito.mockStatic(DynamoDbClientProvider.class);
        mockClientProvider.when(DynamoDbClientProvider::getDynamoDbEnhancedClient).thenReturn(mockEnhancedClient);

        // Create the actual schema *before* mocking the static method
        // Use 'var' to let the compiler infer the correct type (BeanTableSchema)
        var actualPlayerSchema = TableSchema.fromBean(Player.class);

        // Mock the static method TableSchema.fromBean
        mockTableSchema = Mockito.mockStatic(TableSchema.class);
        // Use lenient().when() if the method might not always be called or needs flexible matching
        // Return the actual schema we created earlier
        lenient().when(TableSchema.fromBean(Player.class)).thenReturn(actualPlayerSchema);

        // Mock the enhanced client returning the mocked table
        // Use lenient() because getTableName() might use a default if env var is null
        lenient().when(mockEnhancedClient.table(anyString(), any(TableSchema.class))).thenReturn(mockPlayerTable);

        // Instantiate the DAO - this will now use the mocked client and table
        playerDao = new DynamoDbPlayerDao();
    }

    @AfterEach
    void tearDown() {
        // Close the static mocks
        mockClientProvider.close();
        mockTableSchema.close();
    }

    @Test
    void getPlayerById_PlayerExists_ReturnsPlayer() {
        String playerId = "player1";
        Player expectedPlayer = new Player();
        expectedPlayer.setPlayerID(playerId);

        // Mock the table interaction
        when(mockPlayerTable.getItem(any(Key.class))).thenAnswer(invocation -> {
            Key key = invocation.getArgument(0);
            if (key.partitionKeyValue().s().equals(playerId)) {
                return expectedPlayer;
            }
            return null;
        });

        Optional<Player> result = playerDao.getPlayerById(playerId);

        assertTrue(result.isPresent());
        assertEquals(expectedPlayer, result.get());
        verify(mockPlayerTable).getItem(eq(Key.builder().partitionValue(playerId).build()));
    }

    @Test
    void getPlayerById_PlayerDoesNotExist_ReturnsEmpty() {
        String playerId = "nonexistent";
        when(mockPlayerTable.getItem(any(Key.class))).thenReturn(null);

        Optional<Player> result = playerDao.getPlayerById(playerId);

        assertFalse(result.isPresent());
        verify(mockPlayerTable).getItem(eq(Key.builder().partitionValue(playerId).build()));
    }

    @Test
    void getPlayerById_DynamoDbException_ReturnsEmpty() {
        String playerId = "player1";
        when(mockPlayerTable.getItem(any(Key.class))).thenThrow(DynamoDbException.builder().message("Test Exception").build());

        assertThrows(PlayerPersistenceException.class, () -> playerDao.getPlayerById(playerId));
    }

    @Test
    void savePlayer_Success() {
        Player player = new Player();
        player.setPlayerID("player1");

        // No exception expected
        assertDoesNotThrow(() -> playerDao.savePlayer(player));

        verify(mockPlayerTable).putItem(eq(player));
    }

    @Test
    void savePlayer_ConditionalCheckFailed_ThrowsRuntimeException() {
        Player player = new Player();
        player.setPlayerID("player1");

        doThrow(ConditionalCheckFailedException.builder().message("Condition failed").build()).when(mockPlayerTable).putItem(any(Player.class));

        PlayerPersistenceException exception = assertThrows(PlayerPersistenceException.class, () -> playerDao.savePlayer(player));
        assertTrue(exception.getCause() instanceof ConditionalCheckFailedException);
        verify(mockPlayerTable).putItem(eq(player));
    }

     @Test
    void savePlayer_DynamoDbException_ThrowsRuntimeException() {
        Player player = new Player();
        player.setPlayerID("player1");

        doThrow(DynamoDbException.builder().message("DB error").build()).when(mockPlayerTable).putItem(any(Player.class));

        PlayerPersistenceException exception = assertThrows(PlayerPersistenceException.class, () -> playerDao.savePlayer(player));
        assertTrue(exception.getMessage().contains("Error saving player"));
        assertTrue(exception.getCause() instanceof DynamoDbException);
        verify(mockPlayerTable).putItem(eq(player));
    }
} 