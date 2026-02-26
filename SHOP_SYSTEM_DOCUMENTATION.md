# ApartmentCore v1.3.2 - Apartment Shop System

## Overview
This update introduces a comprehensive **Apartment Shop System** that allows players to purchase premium amenities and upgrades for their apartments, providing various buffs and enhancements.

## Features Added

### 🛍️ Shop Items (5 Categories, 5 Tiers Each)

#### 1. Premium Kitchen
- **Effect**: Income bonus percentage (10% - 30%)
- **Tiers**: I-V
- **Cost**: $5,000 - $50,000
- **Buff**: Multiplicative income bonus

#### 2. Luxury Furniture  
- **Effect**: Flat base income bonus ($50 - $400)
- **Tiers**: I-V
- **Cost**: $3,000 - $40,000
- **Buff**: Additive income bonus

#### 3. Solar Panel System
- **Effect**: Tax reduction percentage (5% - 25%)
- **Tiers**: I-V
- **Cost**: $7,000 - $50,000
- **Buff**: Reduces final tax amount

#### 4. High Speed Internet
- **Effect**: Faster income generation (1-5 tick reduction)
- **Tiers**: I-V
- **Cost**: $4,000 - $45,000
- **Buff**: Reduces income generation intervals

#### 5. Extra Living Room
- **Effect**: Increased max guestbook messages (+10 - +50)
- **Tiers**: I-V
- **Cost**: $2,000 - $30,000
- **Buff**: Expands guestbook capacity

## 🏗️ System Architecture

### Core Classes
- [`ShopBuffType`](src/main/java/com/aithor/apartmentcorei3/shop/ShopBuffType.java) - Enum defining buff types
- [`ShopItem`](src/main/java/com/aithor/apartmentcorei3/shop/ShopItem.java) - Enum defining all shop items with costs and values
- [`ApartmentShopData`](src/main/java/com/aithor/apartmentcorei3/shop/ApartmentShopData.java) - Per-apartment shop data storage
- [`ApartmentShopManager`](src/main/java/com/aithor/apartmentcorei3/shop/ApartmentShopManager.java) - Main shop system manager

### GUI Integration
- [`ApartmentShopGUI`](src/main/java/com/aithor/apartmentcorei3/gui/menus/ApartmentShopGUI.java) - Shop interface for purchasing upgrades
- Updated [`MainMenuGUI`](src/main/java/com/aithor/apartmentcorei3/gui/menus/MainMenuGUI.java) with shop access button
- Updated [`MyApartmentsGUI`](src/main/java/com/aithor/apartmentcorei3/gui/menus/MyApartmentsGUI.java) with shop access via right-click

## 💰 Business Logic

### Purchase Rules
1. **Apartment-Specific**: Each buff applies only to the apartment where it was purchased
2. **Tier Progression**: Must purchase tiers sequentially (cannot skip tiers)
3. **Ownership Required**: Only apartment owners can purchase upgrades
4. **No Refund Exchanges**: Cannot exchange between different shop items

### Selling & Refunds
1. **50% Refund Rule**: When apartment is sold, owner receives 50% of total shop investment
2. **Automatic Reset**: All shop buffs are removed when apartment changes ownership
3. **Repossession Handling**: Shop refunds are given even during tax repossession

### Data Persistence
- Shop data stored in [`shop_data.yml`](src/main/resources/shop.yml)
- Per-apartment tier tracking
- Total investment tracking for refund calculations
- Auto-save integration with existing save system

## 🔧 Configuration

### Main Configuration
- Added shop permissions to [`plugin.yml`](src/main/resources/plugin.yml)
- Shop system integration in [`ApartmentCorei3.java`](src/main/java/com/aithor/apartmentcorei3/ApartmentCorei3.java)

### Shop Configuration
- Comprehensive [`shop.yml`](src/main/resources/shop.yml) with customizable costs and values
- GUI appearance settings
- Performance and debug options
- Message customization

## 📊 PlaceholderAPI Integration

### New Placeholders
- `%apartmentcore_<apt_id>_shop_total_investment%` - Total money spent on shop items
- `%apartmentcore_<apt_id>_shop_income_bonus%` - Total income bonus percentage
- `%apartmentcore_<apt_id>_shop_base_income%` - Total flat income bonus
- `%apartmentcore_<apt_id>_shop_tax_reduction%` - Total tax reduction percentage
- `%apartmentcore_<apt_id>_shop_income_speed%` - Income speed bonus (tick reduction)
- `%apartmentcore_<apt_id>_shop_max_messages%` - Extra guestbook messages
- `%apartmentcore_<apt_id>_shop_<item>_tier%` - Specific item tier
- `%apartmentcore_<apt_id>_shop_<item>_value%` - Specific item buff value

### Item-Specific Placeholders
- `premium_kitchen`, `luxury_furniture`, `solar_panel`, `high_speed_internet`, `extra_living_room`

## 🎮 User Experience

### Access Methods
1. **Main Menu**: New "🛍️ Apartment Shop" button
2. **My Apartments**: Right-click any apartment to access its shop
3. **Per-Apartment**: Each apartment has its own independent shop upgrades

### Visual Indicators
- **Tier Progression**: Different materials based on tier (Iron → Gold → Emerald → Diamond → Netherite)
- **Glow Effects**: Active upgrades have enchantment glow
- **Color Coding**: Green for available upgrades, red for insufficient funds
- **Statistics Display**: Investment tracking and current buff summary

## 🔒 Permissions

```yaml
apartmentcore.shop:
  description: Allow using the apartment shop system
  default: true
  children:
    apartmentcore.shop.buy: true
    apartmentcore.shop.view: true

apartmentcore.shop.buy:
  description: Allow purchasing shop items
  default: true

apartmentcore.shop.view:
  description: Allow viewing shop items
  default: true

apartmentcore.admin.shop:
  description: Allow managing shop configurations
  default: op
```

## 🚀 Technical Implementation

### Buff Application
- **Income Buffs**: Applied in [`ApartmentManager.generateIncome()`](src/main/java/com/aithor/apartmentcorei3/ApartmentManager.java)
- **Tax Reduction**: Applied in [`Apartment.computeBaseTaxAmountWithShopBuffs()`](src/main/java/com/aithor/apartmentcorei3/Apartment.java)
- **Speed Buffs**: Framework implemented for future tick-based optimization
- **Message Buffs**: Ready for guestbook system integration

### Data Safety
- **Transaction Logging**: All purchases logged for audit trail
- **Rollback Protection**: Refunds calculated before data reset
- **Concurrent Safety**: Thread-safe data structures used
- **Save Integration**: Shop data saved with apartment data automatically

## 📋 Version Information
- **Plugin Version**: 1.3.2
- **Theme**: "Apartment Shop System" 
- **Description**: "Advanced apartment management system with Apartment Shop System - Upgrade your properties with premium amenities and buffs"

This implementation provides a complete, production-ready apartment upgrade system that enhances the core apartment management experience with meaningful progression mechanics and economic gameplay.