package com.aithor.apartmentcorei3.gui.menus;

import com.aithor.apartmentcorei3.Apartment;
import com.aithor.apartmentcorei3.ApartmentCorei3;
import com.aithor.apartmentcorei3.ApartmentRating;
import com.aithor.apartmentcorei3.gui.GUIManager;
import com.aithor.apartmentcorei3.gui.interfaces.PaginatedGUI;
import com.aithor.apartmentcorei3.gui.items.GUIItem;
import com.aithor.apartmentcorei3.gui.items.ItemBuilder;
import com.aithor.apartmentcorei3.gui.utils.GUIUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GUI for browsing available apartments
 */
public class ApartmentBrowserGUI extends PaginatedGUI {
    
    private final ApartmentCorei3 plugin;
    private final GUIManager guiManager;
    
    // Filter states
    private FilterType currentFilter = FilterType.ALL;
    private SortType currentSort = SortType.PRICE_LOW;
    
    // Filter slots
    private static final int FILTER_ALL_SLOT = 1;
    private static final int FILTER_CHEAP_SLOT = 2;
    private static final int FILTER_EXPENSIVE_SLOT = 3;
    private static final int FILTER_RATED_SLOT = 5;
    private static final int FILTER_LEVEL_SLOT = 6;
    private static final int SORT_SLOT = 7;
    private static final int BACK_SLOT = 0;
    
    public ApartmentBrowserGUI(Player player, ApartmentCorei3 plugin, GUIManager guiManager) {
        super(player, ChatColor.DARK_BLUE + "Browse Apartments", 54, 28);
        this.plugin = plugin;
        this.guiManager = guiManager;
    }
    
    @Override
    protected List<GUIItem> loadItems() {
        List<Apartment> apartments = plugin.getApartmentManager().getApartments().values().stream()
                .filter(a -> a.owner == null) // Only available apartments
                .collect(Collectors.toList());
        
        // Apply filters
        apartments = applyFilter(apartments);
        
        // Apply sorting
        apartments = applySort(apartments);
        
        // Convert to GUI items
        List<GUIItem> items = new ArrayList<>();
        for (Apartment apartment : apartments) {
            items.add(createApartmentItem(apartment));
        }
        
        return items;
    }
    
    @Override
    protected void setupInventory() {
        super.setupInventory();
        addFilterAndSortOptions();
    }
    
    private void addFilterAndSortOptions() {
        // Back button
        ItemStack backItem = new ItemBuilder(Material.ARROW)
                .name("&c◀ Back to Main Menu")
                .lore("&7Return to the main menu")
                .build();
        inventory.setItem(BACK_SLOT, backItem);
        
        // Filter: All
        Material allMaterial = currentFilter == FilterType.ALL ? Material.LIME_CONCRETE : Material.WHITE_CONCRETE;
        ItemStack allFilter = new ItemBuilder(allMaterial)
                .name("&6All Apartments")
                .lore(
                    "&7Show all available apartments",
                    "",
                    currentFilter == FilterType.ALL ? "&a✓ Active filter" : "&7Click to activate"
                )
                .build();
        inventory.setItem(FILTER_ALL_SLOT, allFilter);
        
        // Filter: Cheap (under median price)
        Material cheapMaterial = currentFilter == FilterType.CHEAP ? Material.LIME_CONCRETE : Material.YELLOW_CONCRETE;
        ItemStack cheapFilter = new ItemBuilder(cheapMaterial)
                .name("&6Budget Friendly")
                .lore(
                    "&7Show cheaper apartments",
                    "&7(Below median price)",
                    "",
                    currentFilter == FilterType.CHEAP ? "&a✓ Active filter" : "&7Click to activate"
                )
                .build();
        inventory.setItem(FILTER_CHEAP_SLOT, cheapFilter);
        
        // Filter: Expensive (above median price)
        Material expensiveMaterial = currentFilter == FilterType.EXPENSIVE ? Material.LIME_CONCRETE : Material.ORANGE_CONCRETE;
        ItemStack expensiveFilter = new ItemBuilder(expensiveMaterial)
                .name("&6Premium Properties")
                .lore(
                    "&7Show luxury apartments",
                    "&7(Above median price)",
                    "",
                    currentFilter == FilterType.EXPENSIVE ? "&a✓ Active filter" : "&7Click to activate"
                )
                .build();
        inventory.setItem(FILTER_EXPENSIVE_SLOT, expensiveFilter);
        
        // Filter: Top Rated
        Material ratedMaterial = currentFilter == FilterType.TOP_RATED ? Material.LIME_CONCRETE : Material.GOLD_BLOCK;
        ItemStack ratedFilter = new ItemBuilder(ratedMaterial)
                .name("&6Top Rated")
                .lore(
                    "&7Show highly rated apartments",
                    "&7(Rating 4.0+ stars)",
                    "",
                    currentFilter == FilterType.TOP_RATED ? "&a✓ Active filter" : "&7Click to activate"
                )
                .build();
        inventory.setItem(FILTER_RATED_SLOT, ratedFilter);
        
        // Filter: High Level
        Material levelMaterial = currentFilter == FilterType.HIGH_LEVEL ? Material.LIME_CONCRETE : Material.DIAMOND_BLOCK;
        ItemStack levelFilter = new ItemBuilder(levelMaterial)
                .name("&6High Level")
                .lore(
                    "&7Show level 3+ apartments",
                    "&7(Better income potential)",
                    "",
                    currentFilter == FilterType.HIGH_LEVEL ? "&a✓ Active filter" : "&7Click to activate"
                )
                .build();
        inventory.setItem(FILTER_LEVEL_SLOT, levelFilter);
        
        // Sort options
        ItemStack sortItem = new ItemBuilder(Material.HOPPER)
                .name("&6Sort: " + currentSort.getDisplayName())
                .lore(
                    "&7Current sorting method",
                    "",
                    "&7Available sorts:",
                    "&7• Price: Low to High",
                    "&7• Price: High to Low", 
                    "&7• Rating: High to Low",
                    "&7• Level: High to Low",
                    "",
                    "&a▶ Click to change"
                )
                .build();
        inventory.setItem(SORT_SLOT, sortItem);
    }
    
    private List<Apartment> applyFilter(List<Apartment> apartments) {
        switch (currentFilter) {
            case CHEAP:
                double medianPrice = apartments.stream()
                        .mapToDouble(a -> a.price)
                        .sorted()
                        .skip(apartments.size() / 2)
                        .findFirst()
                        .orElse(0);
                return apartments.stream()
                        .filter(a -> a.price <= medianPrice)
                        .collect(Collectors.toList());
                        
            case EXPENSIVE:
                double medianPrice2 = apartments.stream()
                        .mapToDouble(a -> a.price)
                        .sorted()
                        .skip(apartments.size() / 2)
                        .findFirst()
                        .orElse(0);
                return apartments.stream()
                        .filter(a -> a.price > medianPrice2)
                        .collect(Collectors.toList());
                        
            case TOP_RATED:
                return apartments.stream()
                        .filter(a -> {
                            ApartmentRating rating = plugin.getApartmentManager().getRating(a.id);
                            return rating != null && rating.getAverageRating() >= 4.0;
                        })
                        .collect(Collectors.toList());
                        
            case HIGH_LEVEL:
                return apartments.stream()
                        .filter(a -> a.level >= 3)
                        .collect(Collectors.toList());
                        
            default:
                return apartments;
        }
    }
    
    private List<Apartment> applySort(List<Apartment> apartments) {
        switch (currentSort) {
            case PRICE_LOW:
                return apartments.stream()
                        .sorted(Comparator.comparingDouble(a -> a.price))
                        .collect(Collectors.toList());
                        
            case PRICE_HIGH:
                return apartments.stream()
                        .sorted(Comparator.comparingDouble((Apartment a) -> a.price).reversed())
                        .collect(Collectors.toList());
                        
            case RATING:
                return apartments.stream()
                        .sorted((a1, a2) -> {
                            ApartmentRating r1 = plugin.getApartmentManager().getRating(a1.id);
                            ApartmentRating r2 = plugin.getApartmentManager().getRating(a2.id);
                            double rating1 = r1 != null ? r1.getAverageRating() : 0;
                            double rating2 = r2 != null ? r2.getAverageRating() : 0;
                            return Double.compare(rating2, rating1);
                        })
                        .collect(Collectors.toList());
                        
            case LEVEL:
                return apartments.stream()
                        .sorted(Comparator.comparingInt((Apartment a) -> a.level).reversed())
                        .collect(Collectors.toList());
                        
            default:
                return apartments;
        }
    }
    
    private GUIItem createApartmentItem(Apartment apartment) {
        ApartmentRating rating = plugin.getApartmentManager().getRating(apartment.id);
        double avgRating = rating != null ? rating.getAverageRating() : 0;
        String ratingDisplay = rating != null && rating.ratingCount > 0 
            ? String.format("%.1f⭐ (%d reviews)", avgRating, rating.ratingCount)
            : "No ratings yet";
        
        // Get level config for income display
        var levelConfig = plugin.getConfigManager().getLevelConfig(apartment.level);
        String incomeRange = levelConfig != null 
            ? plugin.getConfigManager().formatMoney(levelConfig.minIncome) + " - " + plugin.getConfigManager().formatMoney(levelConfig.maxIncome)
            : "Unknown";
        
        ItemStack item = new ItemBuilder(Material.DARK_OAK_DOOR)
                .name("&6🏠 " + apartment.displayName)
                .lore(
                    "&7ID: &f" + apartment.id,
                    "&7Location: &f" + apartment.worldName,
                    "",
                    "&e💰 Price: &f" + plugin.getConfigManager().formatMoney(apartment.price),
                    "&e📊 Level: &f" + apartment.level + "/5",
                    "&e💸 Income: &f" + incomeRange + "/hour",
                    "&e⭐ Rating: &f" + ratingDisplay,
                    "",
                    "&a▶ Left-click to view details",
                    "&a▶ Right-click to buy instantly",
                    "&a▶ Shift+click to teleport & preview"
                )
                .glow()
                .build();
        
        return new GUIItem(item, apartment.id, apartment);
    }
    
    @Override
    protected void handleItemClick(GUIItem item, InventoryClickEvent event) {
        Apartment apartment = item.getData(Apartment.class);
        if (apartment == null) return;
        
        ClickType clickType = event.getClick();
        
        if (clickType == ClickType.RIGHT) {
            // Instant buy
            handleInstantBuy(apartment);
        } else if (clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT) {
            // Teleport to preview
            handlePreview(apartment);
        } else {
            // View details
            guiManager.openApartmentDetails(player, apartment.id);
        }
    }
    
    @Override
    public void handleClick(InventoryClickEvent event) {
        // Handle filter/sort clicks first
        int slot = event.getSlot();
        
        if (slot == BACK_SLOT) {
            guiManager.openMainMenu(player);
            return;
        }
        
        if (slot == FILTER_ALL_SLOT) {
            currentFilter = FilterType.ALL;
            refresh();
            GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);
            return;
        }
        
        if (slot == FILTER_CHEAP_SLOT) {
            currentFilter = FilterType.CHEAP;
            refresh();
            GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);
            return;
        }
        
        if (slot == FILTER_EXPENSIVE_SLOT) {
            currentFilter = FilterType.EXPENSIVE;
            refresh();
            GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);
            return;
        }
        
        if (slot == FILTER_RATED_SLOT) {
            currentFilter = FilterType.TOP_RATED;
            refresh();
            GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);
            return;
        }
        
        if (slot == FILTER_LEVEL_SLOT) {
            currentFilter = FilterType.HIGH_LEVEL;
            refresh();
            GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);
            return;
        }
        
        if (slot == SORT_SLOT) {
            // Cycle through sort types
            currentSort = currentSort.next();
            refresh();
            GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);
            return;
        }
        
        // Handle pagination and items
        super.handleClick(event);
    }
    
    private void handleInstantBuy(Apartment apartment) {
        player.closeInventory();
        
        // Use existing buy command logic
        plugin.getServer().dispatchCommand(player, "apartmentcore buy " + apartment.id);
    }
    
    private void handlePreview(Apartment apartment) {
        if (player.hasPermission("apartmentcore.preview")) {
            plugin.getApartmentManager().teleportToApartment(player, apartment.id, true);
            GUIUtils.sendMessage(player, "&aYou are now previewing &e" + apartment.displayName);
            GUIUtils.sendMessage(player, "&7Use &f/apartmentcore buy " + apartment.id + " &7to purchase");
        } else {
            GUIUtils.sendMessage(player, "&cYou don't have permission to preview apartments!");
            GUIUtils.playSound(player, GUIUtils.ERROR_SOUND);
        }
    }
    
    private enum FilterType {
        ALL, CHEAP, EXPENSIVE, TOP_RATED, HIGH_LEVEL
    }
    
    private enum SortType {
        PRICE_LOW("Price: Low to High"),
        PRICE_HIGH("Price: High to Low"),
        RATING("Rating: High to Low"),
        LEVEL("Level: High to Low");
        
        private final String displayName;
        
        SortType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public SortType next() {
            SortType[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }
}