package com.assassin.service;

import com.assassin.dao.ItemDao;
import com.assassin.dao.PlayerInventoryDao;
import com.assassin.dao.TransactionDao;
import com.assassin.dao.PlayerDao;
import com.assassin.model.Item;
import com.assassin.model.PlayerInventoryItem;
import com.assassin.model.Transaction;
import com.assassin.model.Player;
import com.assassin.exception.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemDao itemDao;

    @Mock
    private PlayerInventoryDao playerInventoryDao;

    @Mock
    private TransactionDao transactionDao;

    @Mock
    private PlayerDao playerDao;

    private ItemService itemService;

    private Item testItem;
    private Player testPlayer;
    private PlayerInventoryItem testInventoryItem;

    @BeforeEach
    void setUp() {
        itemService = new ItemService(itemDao, playerInventoryDao, transactionDao, playerDao);

        // Create test item
        testItem = new Item();
        testItem.setItemId("test-item-1");
        testItem.setName("Test Radar");
        testItem.setDescription("A test radar item");
        testItem.setItemType(Item.ItemType.RADAR_SCAN);
        testItem.setPrice(1000L); // $10.00 in cents
        testItem.setIsActive(true);
        testItem.setDurationSeconds(3600); // 1 hour
        testItem.setIsUsable(true);
        testItem.setIsStackable(true);

        // Create test player
        testPlayer = new Player();
        testPlayer.setPlayerID("test-player-1");
        testPlayer.setPlayerName("Test Player");
        testPlayer.setEmail("test@example.com");

        // Create test inventory item
        testInventoryItem = new PlayerInventoryItem();
        testInventoryItem.setPlayerId("test-player-1");
        testInventoryItem.setInventoryItemId("inv-item-1");
        testInventoryItem.setItemId("test-item-1");
        testInventoryItem.setQuantity(1);
        testInventoryItem.setAcquiredAt(Instant.now().toString());
    }

    @Test
    void getAllItems_ShouldReturnAllItems_WhenSuccessful() throws PersistenceException {
        // Arrange
        Item localTestItem = new Item();
        localTestItem.setItemId("local-item-for-getAllItems");
        localTestItem.setName("Local Test Radar");
        localTestItem.setDescription("A local test radar item");
        localTestItem.setItemType(Item.ItemType.RADAR_SCAN);
        localTestItem.setPrice(1200L);
        localTestItem.setIsActive(true);
        localTestItem.setDurationSeconds(1800);
        localTestItem.setIsUsable(true);
        localTestItem.setIsStackable(false);

        List<Item> expectedItems = List.of(localTestItem);

        when(itemDao.getAllActiveItems()).thenReturn(expectedItems);

        // Act
        List<Item> result = itemService.getAllItems();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size(), "Expected 1 item but got " + result.size());
        if (!result.isEmpty()) {
            assertEquals(localTestItem.getItemId(), result.get(0).getItemId());
        }
        verify(itemDao).getAllActiveItems();
    }

    @Test
    void getAllItems_ShouldThrowException_WhenDaoFails() throws PersistenceException {
        // Arrange
        when(itemDao.getAllActiveItems()).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        assertThrows(PersistenceException.class, () -> itemService.getAllItems());
        verify(itemDao).getAllActiveItems();
    }

    @Test
    void getItemsByType_ShouldReturnFilteredItems_WhenSuccessful() throws PersistenceException {
        // Arrange
        List<Item> expectedItems = Arrays.asList(testItem);
        when(itemDao.getItemsByType(Item.ItemType.RADAR_SCAN)).thenReturn(expectedItems);

        // Act
        List<Item> result = itemService.getItemsByType(Item.ItemType.RADAR_SCAN);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(Item.ItemType.RADAR_SCAN, result.get(0).getItemType());
        verify(itemDao).getItemsByType(Item.ItemType.RADAR_SCAN);
    }

    @Test
    void getItemsByType_ShouldThrowException_WhenTypeIsNull() {
        // Act & Assert
        assertThrows(PersistenceException.class, () -> itemService.getItemsByType(null));
    }

    @Test
    void getItemById_ShouldReturnItem_WhenItemExists() throws PersistenceException {
        // Arrange
        when(itemDao.getItemById("test-item-1")).thenReturn(Optional.of(testItem));

        // Act
        Optional<Item> result = itemService.getItemById("test-item-1");

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testItem.getItemId(), result.get().getItemId());
        verify(itemDao).getItemById("test-item-1");
    }

    @Test
    void getItemById_ShouldReturnEmpty_WhenItemDoesNotExist() throws PersistenceException {
        // Arrange
        when(itemDao.getItemById("nonexistent")).thenReturn(Optional.empty());

        // Act
        Optional<Item> result = itemService.getItemById("nonexistent");

        // Assert
        assertFalse(result.isPresent());
        verify(itemDao).getItemById("nonexistent");
    }

    @Test
    void getItemById_ShouldThrowException_WhenItemIdIsNull() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> itemService.getItemById(null));
        verifyNoInteractions(itemDao);
    }

    @Test
    void getItemById_ShouldThrowException_WhenItemIdIsEmpty() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> itemService.getItemById(""));
        verifyNoInteractions(itemDao);
    }

    @Test
    void purchaseItem_ShouldCreateTransaction_WhenValidRequest() throws PersistenceException {
        // Arrange
        String paymentMethodId = "pm_test123";
        when(playerDao.findPlayerById("test-player-1")).thenReturn(testPlayer);
        when(itemDao.getItemById("test-item-1")).thenReturn(Optional.of(testItem));

        // Act
        Transaction result = itemService.purchaseItem("test-player-1", "test-item-1", paymentMethodId, 2, "game-1");

        // Assert
        assertNotNull(result);
        assertEquals("test-player-1", result.getPlayerId());
        assertEquals("test-item-1", result.getItemId());
        assertEquals("game-1", result.getGameId());
        assertEquals(2000L, result.getAmount()); // 2 * 1000 cents
        assertEquals(Transaction.TransactionType.IAP_ITEM, result.getTransactionType());
        assertEquals(Transaction.TransactionStatus.PENDING, result.getStatus());
        assertEquals(paymentMethodId, result.getPaymentMethodDetails());
        verify(playerDao).findPlayerById("test-player-1");
        verify(itemDao).getItemById("test-item-1");
        verify(transactionDao).saveTransaction(any(Transaction.class));
    }

    @Test
    void purchaseItem_ShouldThrowException_WhenPlayerNotFound() throws PersistenceException {
        // Arrange
        when(playerDao.findPlayerById("nonexistent")).thenReturn(null);

        // Act & Assert
        assertThrows(PersistenceException.class, () -> itemService.purchaseItem("nonexistent", "test-item-1", "payment-method-1"));
    }

    @Test
    void purchaseItem_ShouldThrowException_WhenItemNotFound() throws PersistenceException {
        // Arrange
        when(playerDao.findPlayerById("test-player-1")).thenReturn(testPlayer);
        when(itemDao.getItemById("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(PersistenceException.class, () -> itemService.purchaseItem("test-player-1", "nonexistent", "payment-method-1"));
    }

    @Test
    void purchaseItem_ShouldThrowException_WhenItemNotAvailable() throws PersistenceException {
        // Arrange
        when(playerDao.findPlayerById("test-player-1")).thenReturn(testPlayer);
        testItem.setIsActive(false);
        when(itemDao.getItemById("test-item-1")).thenReturn(Optional.of(testItem));

        // Act & Assert
        assertThrows(PersistenceException.class, () -> itemService.purchaseItem("test-player-1", "test-item-1", "payment-method-1"));
    }

    @Test
    void purchaseItem_ShouldThrowException_WhenQuantityIsZero() throws PersistenceException {
        // Arrange
        // when(playerDao.findPlayerById("test-player-1")).thenReturn(testPlayer); // REMOVED: Not needed since validation fails early

        // Act & Assert
        assertThrows(PersistenceException.class, () -> itemService.purchaseItem("test-player-1", "test-item-1", "payment-method-1", 0, "game-1"));
    }

    @Test
    void purchaseItem_ShouldThrowException_WhenQuantityIsNegative() throws PersistenceException {
        // Arrange
        // when(playerDao.findPlayerById("test-player-1")).thenReturn(testPlayer); // REMOVED: Not needed since validation fails early

        // Act & Assert
        assertThrows(PersistenceException.class, () -> itemService.purchaseItem("test-player-1", "test-item-1", "payment-method-1", -1, "game-1"));
    }

    @Test
    void grantItemToPlayer_ShouldGrantItem_WhenValidRequest() throws PersistenceException {
        // Arrange
        when(playerDao.findPlayerById("test-player-1")).thenReturn(testPlayer);
        when(itemDao.getItemById("test-item-1")).thenReturn(Optional.of(testItem));
        when(playerInventoryDao.grantItemToPlayer("test-player-1", "test-item-1", 3, "game-1"))
            .thenReturn(testInventoryItem);

        // Act
        PlayerInventoryItem result = itemService.grantItemToPlayer("test-player-1", "test-item-1", 3, "game-1");

        // Assert
        assertNotNull(result);
        assertEquals(testInventoryItem.getPlayerId(), result.getPlayerId());
        assertEquals(testInventoryItem.getItemId(), result.getItemId());
        verify(playerDao).findPlayerById("test-player-1");
        verify(itemDao).getItemById("test-item-1");
        verify(playerInventoryDao).grantItemToPlayer("test-player-1", "test-item-1", 3, "game-1");
    }

    @Test
    void grantItemToPlayer_ShouldThrowException_WhenPlayerNotFound() throws PersistenceException {
        // Arrange
        when(playerDao.findPlayerById("nonexistent")).thenReturn(null);

        // Act & Assert
        assertThrows(PersistenceException.class, () -> itemService.grantItemToPlayer("nonexistent", "test-item-1", 1, "game-1"));
    }

    @Test
    void grantItemToPlayer_ShouldThrowException_WhenItemNotFound() throws PersistenceException {
        // Arrange
        when(playerDao.findPlayerById("test-player-1")).thenReturn(testPlayer);
        when(itemDao.getItemById("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(PersistenceException.class, () -> itemService.grantItemToPlayer("test-player-1", "nonexistent", 1, "game-1"));
    }

    @Test
    void grantItemToPlayer_ShouldThrowException_WhenQuantityIsZero() throws PersistenceException {
        // Arrange
        // when(playerDao.findPlayerById("test-player-1")).thenReturn(testPlayer); // REMOVED: Not needed since validation fails early

        // Act & Assert
        assertThrows(PersistenceException.class, () -> itemService.grantItemToPlayer("test-player-1", "test-item-1", 0, "game-1"));
    }

    @Test
    void getPlayerInventory_ShouldReturnInventory_WhenSuccessful() throws PersistenceException {
        // Arrange
        List<PlayerInventoryItem> expectedInventory = Arrays.asList(testInventoryItem);
        when(playerInventoryDao.getPlayerInventory("test-player-1")).thenReturn(expectedInventory);
        when(playerDao.findPlayerById("test-player-1")).thenReturn(testPlayer);

        // Act
        List<PlayerInventoryItem> result = itemService.getPlayerInventory("test-player-1");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testInventoryItem.getInventoryItemId(), result.get(0).getInventoryItemId());
        verify(playerDao).findPlayerById("test-player-1");
        verify(playerInventoryDao).getPlayerInventory("test-player-1");
    }

    @Test
    void getPlayerInventory_ShouldThrowException_WhenPlayerNotFound() throws PersistenceException {
        // Arrange
        when(playerDao.findPlayerById("nonexistent")).thenReturn(null);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            itemService.getPlayerInventory("nonexistent"));
        verify(playerDao).findPlayerById("nonexistent");
        verifyNoInteractions(playerInventoryDao);
    }

    @Test
    void getTotalItemQuantityForPlayer_ShouldReturnQuantity_WhenSuccessful() throws PersistenceException {
        // Arrange
        when(playerDao.findPlayerById("test-player-1")).thenReturn(testPlayer);
        when(playerInventoryDao.getTotalItemQuantityForPlayer("test-player-1", "test-item-1")).thenReturn(5);

        // Act
        int result = itemService.getTotalItemQuantityForPlayer("test-player-1", "test-item-1");

        // Assert
        assertEquals(5, result);
        verify(playerDao).findPlayerById("test-player-1");
        verify(playerInventoryDao).getTotalItemQuantityForPlayer("test-player-1", "test-item-1");
    }

    @Test
    void getTotalItemQuantityForPlayer_ShouldThrowException_WhenPlayerNotFound() throws PersistenceException {
        // Arrange
        when(playerDao.findPlayerById("nonexistent")).thenReturn(null);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            itemService.getTotalItemQuantityForPlayer("nonexistent", "test-item-1"));
        verify(playerDao).findPlayerById("nonexistent");
        verifyNoInteractions(playerInventoryDao);
    }

    @Test
    void getItemsByPriceRange_ShouldReturnFilteredItems_WhenSuccessful() throws PersistenceException {
        // Arrange
        List<Item> expectedItems = Arrays.asList(testItem);
        when(itemDao.getItemsByPriceRange(500L, 1500L)).thenReturn(expectedItems);

        // Act
        List<Item> result = itemService.getItemsByPriceRange(500L, 1500L);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testItem.getItemId(), result.get(0).getItemId());
        verify(itemDao).getItemsByPriceRange(500L, 1500L);
    }

    @Test
    void getItemsByPriceRange_ShouldThrowException_WhenMinPriceIsNegative() throws PersistenceException {
        // Act & Assert
        assertThrows(PersistenceException.class, () -> itemService.getItemsByPriceRange(-1L, 1000L));
    }

    @Test
    void getItemsByPriceRange_ShouldThrowException_WhenMaxPriceIsNegative() throws PersistenceException {
        // Act & Assert
        assertThrows(PersistenceException.class, () -> itemService.getItemsByPriceRange(0L, -1L));
    }

    @Test
    void getItemsByPriceRange_ShouldThrowException_WhenMinPriceGreaterThanMaxPrice() throws PersistenceException {
        // Act & Assert
        assertThrows(PersistenceException.class, () -> itemService.getItemsByPriceRange(1000L, 500L));
    }

    // Note: updateItemAvailability method doesn't exist in ItemService
    // The ItemDao has updateItemActiveStatus method, but ItemService doesn't expose it
    // These tests would be for the DAO layer, not the service layer

    // Edge case tests
    @Test
    void purchaseItem_WithDefaultQuantityAndNullGameId_ShouldSucceed() throws PersistenceException {
        // Arrange
        String paymentMethodId = "pm_test123";
        when(playerDao.findPlayerById("test-player-1")).thenReturn(testPlayer);
        when(itemDao.getItemById("test-item-1")).thenReturn(Optional.of(testItem));

        // Act
        Transaction result = itemService.purchaseItem("test-player-1", "test-item-1", paymentMethodId);

        // Assert
        assertNotNull(result);
        assertEquals(1000L, result.getAmount()); // 1 * 1000 cents (default quantity = 1)
        assertNull(result.getGameId());
        verify(transactionDao).saveTransaction(any(Transaction.class));
    }

    @Test
    void purchaseItem_ShouldThrowException_WhenPaymentMethodIdIsNull() throws PersistenceException {
        // Arrange
        // when(playerDao.findPlayerById("test-player-1")).thenReturn(testPlayer); // REMOVED: Not needed since validation fails early

        // Act & Assert
        assertThrows(PersistenceException.class, () -> itemService.purchaseItem("test-player-1", "test-item-1", null));
    }

    @Test
    void purchaseItem_ShouldThrowException_WhenPaymentMethodIdIsEmpty() throws PersistenceException {
        // Arrange
        // when(playerDao.findPlayerById("test-player-1")).thenReturn(testPlayer); // REMOVED: Not needed since validation fails early

        // Act & Assert
        assertThrows(PersistenceException.class, () -> itemService.purchaseItem("test-player-1", "test-item-1", ""));
    }
} 