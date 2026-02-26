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
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GUI for browsing available apartments
 */
public class ApartmentBrowserGUI extends PaginatedGUI {
    
    private final ApartmentCorei3 plugin;
    private final GUIManager guiManager;

    // YAML menu section and placeholder context
    private ConfigurationSection menuSection;
    private final Map<String, String> context = new HashMap<>();

    // Filter states
    private FilterType currentFilter = FilterType.ALL;
    private SortType currentSort = SortType.PRICE_LOW;

    // Default slots (can be overridden by apartment_gui.yml)
    private static final int DEFAULT_FILTER_ALL_SLOT = 1;
    private static final int DEFAULT_FILTER_CHEAP_SLOT = 2;
    private static final int DEFAULT_FILTER_EXPENSIVE_SLOT = 3;
    private static final int DEFAULT_FILTER_RATED_SLOT = 5;
    private static final int DEFAULT_FILTER_LEVEL_SLOT = 6;
    private static final int DEFAULT_SORT_SLOT = 7;
    private static final int DEFAULT_BACK_SLOT = 0;

    // Resolved per-instance slots (read from apartment_gui.yml when present)
    private int filterAllSlot = DEFAULT_FILTER_ALL_SLOT;
    private int filterCheapSlot = DEFAULT_FILTER_CHEAP_SLOT;
    private int filterExpensiveSlot = DEFAULT_FILTER_EXPENSIVE_SLOT;
    private int filterRatedSlot = DEFAULT_FILTER_RATED_SLOT;
    private int filterLevelSlot = DEFAULT_FILTER_LEVEL_SLOT;
    private int sortSlot = DEFAULT_SORT_SLOT;
    private int backSlot = DEFAULT_BACK_SLOT;
    
    public ApartmentBrowserGUI(Player player, ApartmentCorei3 plugin, GUIManager guiManager) {
        super(player, ChatColor.DARK_BLUE + "Browse Apartments", 54, 28);
        this.plugin = plugin;
        this.guiManager = guiManager;

        // Load menu overrides from external GUI config (apartment_gui.yml)
        this.menuSection = plugin.getConfigManager().getGuiMenuSection("apartment-browser");

        // Resolve slot positions from config if provided
        if (menuSection != null) {
            ConfigurationSection controls = menuSection.getConfigurationSection("controls");
            if (controls != null) {
                this.backSlot = controls.getInt("back.slot", DEFAULT_BACK_SLOT);
                this.filterAllSlot = controls.getInt("filter-all.slot", DEFAULT_FILTER_ALL_SLOT);
                this.filterCheapSlot = controls.getInt("filter-cheap.slot", DEFAULT_FILTER_CHEAP_SLOT);
                this.filterExpensiveSlot = controls.getInt("filter-expensive.slot", DEFAULT_FILTER_EXPENSIVE_SLOT);
                this.filterRatedSlot = controls.getInt("filter-rated.slot", DEFAULT_FILTER_RATED_SLOT);
                this.filterLevelSlot = controls.getInt("filter-level.slot", DEFAULT_FILTER_LEVEL_SLOT);
                this.sortSlot = controls.getInt("sort.slot", DEFAULT_SORT_SLOT);
            }
        }
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
        buildContext();
        addFilterAndSortOptions();
    }
    
    private void addFilterAndSortOptions() {
        // Back button
        ItemStack backItem = new ItemBuilder(Material.ARROW)
                .name("&c◀ Back to Main Menu")
                .lore("&7Return to the main menu")
                .build();
        backItem = applyControlOverrides("back", backItem);
        if (backItem != null) {
            inventory.setItem(backSlot, backItem);
        }
        
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
        allFilter = applyControlOverrides("filter-all", allFilter);
        if (allFilter != null) {
            inventory.setItem(filterAllSlot, allFilter);
        }
        
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
        cheapFilter = applyControlOverrides("filter-cheap", cheapFilter);
        if (cheapFilter != null) {
            inventory.setItem(filterCheapSlot, cheapFilter);
        }
        
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
        expensiveFilter = applyControlOverrides("filter-expensive", expensiveFilter);
        if (expensiveFilter != null) {
            inventory.setItem(filterExpensiveSlot, expensiveFilter);
        }
        
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
        ratedFilter = applyControlOverrides("filter-rated", ratedFilter);
        if (ratedFilter != null) {
            inventory.setItem(filterRatedSlot, ratedFilter);
        }
        
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
        levelFilter = applyControlOverrides("filter-level", levelFilter);
        if (levelFilter != null) {
            inventory.setItem(filterLevelSlot, levelFilter);
        }
        
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
        sortItem = applyControlOverrides("sort", sortItem);
        if (sortItem != null) {
            inventory.setItem(sortSlot, sortItem);
        }
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
        
        if (slot == backSlot) {
            guiManager.openMainMenu(player);
            return;
        }

        if (slot == filterAllSlot) {
            currentFilter = FilterType.ALL;
            refresh();
            GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);
            return;
        }

        if (slot == filterCheapSlot) {
            currentFilter = FilterType.CHEAP;
            refresh();
            GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);
            return;
        }

        if (slot == filterExpensiveSlot) {
            currentFilter = FilterType.EXPENSIVE;
            refresh();
            GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);
            return;
        }

        if (slot == filterRatedSlot) {
            currentFilter = FilterType.TOP_RATED;
            refresh();
            GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);
            return;
        }

        if (slot == filterLevelSlot) {
            currentFilter = FilterType.HIGH_LEVEL;
            refresh();
            GUIUtils.playSound(player, GUIUtils.CLICK_SOUND);
            return;
        }

        if (slot == sortSlot) {
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

    // ======================
    // Helpers for YAML config
    // ======================
    private ItemStack applyControlOverrides(String key, ItemStack defaultItem) {
        if (menuSection == null) return defaultItem;
        ConfigurationSection controls = menuSection.getConfigurationSection("controls");
        if (controls == null) return defaultItem;
        ConfigurationSection sec = controls.getConfigurationSection(key);
        if (sec == null) return defaultItem;

        boolean enabled = sec.getBoolean("enabled", true);
        if (!enabled) return null;

        String materialName = sec.getString("material", null);
        Material mat = materialName != null ? parseMaterial(materialName, defaultItem.getType()) : defaultItem.getType();

        String name = sec.getString("name", null);
        List<String> lore = sec.isList("lore") ? sec.getStringList("lore") : null;
        boolean glow = sec.getBoolean("glow", false);
        int customModelData = sec.getInt("custom-model-data", 0);

        // Allow partial overrides - if name/lore not provided, use default but still apply other properties
        ItemBuilder builder = new ItemBuilder(mat);

        if (name != null) {
            builder.name(colorize(replacePlaceholders(name)));
        } else {
            // Keep default name - we can't easily extract it from defaultItem, so return as-is
            return defaultItem;
        }

        if (lore != null) {
            List<String> colored = new ArrayList<>();
            for (String line : lore) {
                colored.add(colorize(replacePlaceholders(line)));
            }
            builder.lore(colored.toArray(new String[0]));
        } else {
            // Keep default lore - same limitation as name
            return defaultItem;
        }

        if (customModelData > 0) {
            builder.modelData(customModelData);
        }
        List<String> colored = new ArrayList<>();
        for (String line : lore) {
            colored.add(colorize(replacePlaceholders(line)));
        }
        builder.lore(colored.toArray(new String[0]));
        if (glow) builder.glow();
        return builder.build();
    }

    private String replacePlaceholders(String s) {
        if (s == null) return null;
        String out = s;
        for (Map.Entry<String, String> e : context.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    private String colorize(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null) return fallback;
        try {
            return Material.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private void buildContext() {
        context.clear();
        context.put("%current_sort%", currentSort.getDisplayName());
    }
}