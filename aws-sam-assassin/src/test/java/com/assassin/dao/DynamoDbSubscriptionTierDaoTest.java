package com.assassin.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assassin.model.SubscriptionBenefits;
import com.assassin.model.SubscriptionTier;

import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;

@ExtendWith(MockitoExtension.class)
class DynamoDbSubscriptionTierDaoTest {

    @Mock
    private DynamoDbEnhancedClient mockEnhancedClient;

    @Mock
    private DynamoDbTable<SubscriptionTier> mockTable;

    // No @InjectMocks, DAO is constructed manually
    private DynamoDbSubscriptionTierDao subscriptionTierDao;

    private SubscriptionTier testTier1, testTier2, inactiveTier;
    private static final String TEST_TABLE_NAME = "test-subscription-tiers-table";

    @BeforeEach
    void setUp() {
        // The DAO will use this mockEnhancedClient, which is configured to return mockTable
        when(mockEnhancedClient.table(eq(TEST_TABLE_NAME), any(TableSchema.class)))
                 .thenReturn(mockTable);

        // Manually construct the DAO using the test-specific constructor
        subscriptionTierDao = new DynamoDbSubscriptionTierDao(TEST_TABLE_NAME, mockEnhancedClient);

        SubscriptionBenefits benefits1 = new SubscriptionBenefits();
        benefits1.setMonthlyCurrencyBonus(100);

        testTier1 = new SubscriptionTier();
        testTier1.setTierId("basic");
        testTier1.setName("Basic Tier");
        testTier1.setIsActive(true);
        testTier1.setDisplayOrder(1);
        testTier1.setCurrency("USD");
        testTier1.setMonthlyPriceCents(1000L);
        testTier1.setBenefits(benefits1);

        SubscriptionBenefits benefits2 = new SubscriptionBenefits();
        benefits2.setPriorityAccess(true);

        testTier2 = new SubscriptionTier();
        testTier2.setTierId("premium");
        testTier2.setName("Premium Tier");
        testTier2.setIsActive(true);
        testTier2.setDisplayOrder(2);
        testTier2.setCurrency("USD");
        testTier2.setMonthlyPriceCents(2000L);
        testTier2.setBenefits(benefits2);
        
        inactiveTier = new SubscriptionTier();
        inactiveTier.setTierId("old");
        inactiveTier.setName("Old Tier");
        inactiveTier.setIsActive(false);
        inactiveTier.setDisplayOrder(3);
    }

    @Test
    void getTierById_whenTierExists_returnsTier() {
        when(mockTable.getItem(any(Key.class))).thenReturn(testTier1);

        Optional<SubscriptionTier> result = subscriptionTierDao.getTierById("basic");

        assertTrue(result.isPresent());
        assertEquals("basic", result.get().getTierId());
        verify(mockTable).getItem(Key.builder().partitionValue("basic").build());
    }

    @Test
    void getTierById_whenTierDoesNotExist_returnsEmpty() {
        when(mockTable.getItem(any(Key.class))).thenReturn(null);

        Optional<SubscriptionTier> result = subscriptionTierDao.getTierById("nonexistent");

        assertFalse(result.isPresent());
        verify(mockTable).getItem(Key.builder().partitionValue("nonexistent").build());
    }

    @Test
    void getAllTiers_returnsAllTiers() {
        PageIterable<SubscriptionTier> mockPageIterable = mock(PageIterable.class);
        SdkIterable<SubscriptionTier> mockSdkIterable = mock(SdkIterable.class);
        List<SubscriptionTier> allTiersList = List.of(testTier1, testTier2, inactiveTier);
        
        when(mockTable.scan()).thenReturn(mockPageIterable);
        when(mockPageIterable.items()).thenReturn(mockSdkIterable);
        when(mockSdkIterable.stream()).thenReturn(allTiersList.stream());

        List<SubscriptionTier> results = subscriptionTierDao.getAllTiers();

        assertEquals(3, results.size());
        assertTrue(results.contains(testTier1));
        assertTrue(results.contains(testTier2));
        assertTrue(results.contains(inactiveTier));
        verify(mockTable).scan();
    }

    @Test
    void getAllActiveTiersOrdered_returnsActiveTiersSorted() {
        PageIterable<SubscriptionTier> mockPageIterable = mock(PageIterable.class);
        SdkIterable<SubscriptionTier> mockSdkIterable = mock(SdkIterable.class);
        List<SubscriptionTier> activeTiersFromScan = new ArrayList<>(List.of(testTier2, testTier1)); // Simulate some order
        
        when(mockTable.scan(any(java.util.function.Consumer.class))).thenReturn(mockPageIterable);
        when(mockPageIterable.items()).thenReturn(mockSdkIterable);
        when(mockSdkIterable.stream()).thenReturn(activeTiersFromScan.stream());

        List<SubscriptionTier> results = subscriptionTierDao.getAllActiveTiersOrdered();

        assertEquals(2, results.size());
        assertEquals("basic", results.get(0).getTierId(), "Should be sorted by displayOrder: basic (1) then premium (2)");
        assertEquals("premium", results.get(1).getTierId());
        assertTrue(results.stream().allMatch(SubscriptionTier::getIsActive));
        
        verify(mockTable).scan(any(java.util.function.Consumer.class));
    }
    
    @Test
    void getAllTiers_whenNoTiers_returnsEmptyList() {
        PageIterable<SubscriptionTier> mockPageIterable = mock(PageIterable.class);
        SdkIterable<SubscriptionTier> mockSdkIterable = mock(SdkIterable.class);
        when(mockTable.scan()).thenReturn(mockPageIterable);
        when(mockPageIterable.items()).thenReturn(mockSdkIterable);
        when(mockSdkIterable.stream()).thenReturn(Stream.empty());

        List<SubscriptionTier> results = subscriptionTierDao.getAllTiers();
        assertTrue(results.isEmpty());
        verify(mockTable).scan();
    }

    @Test
    void getAllActiveTiersOrdered_whenNoActiveTiers_returnsEmptyList() {
        PageIterable<SubscriptionTier> mockPageIterable = mock(PageIterable.class);
        SdkIterable<SubscriptionTier> mockSdkIterable = mock(SdkIterable.class);
        when(mockTable.scan(any(java.util.function.Consumer.class))).thenReturn(mockPageIterable);
        when(mockPageIterable.items()).thenReturn(mockSdkIterable);
        when(mockSdkIterable.stream()).thenReturn(Stream.empty());

        List<SubscriptionTier> results = subscriptionTierDao.getAllActiveTiersOrdered();
        assertTrue(results.isEmpty());
        verify(mockTable).scan(any(java.util.function.Consumer.class));
    }
} 