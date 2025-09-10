# **ApartmentCore Plugin v1.2.7**

Advanced apartment management system for Minecraft servers with comprehensive features and optimizations.

## Features

- 🏠 **Apartment Converter**: Convert WorldGuard regions into purchasable apartments
- 💰 **Income Generation**: Apartments generate passive income based on their level
- 📊 **Level System**: 5 apartment levels with increasing income rates
- 🏦 **Tax System**: Automatic tax collection with progressive penalties and layered notifications
- 📝 **Buy & Sell**: Players can resell the apartments they have purchased for up to 70% of the original price.
- 📌 **Fast Travel**: allowing apartment owners to instantly teleport to the building's location.
- 🌐 **Easy Configuration**: configuration that controls almost all aspects of the plugin, easy to understand.
- 💎 **Custom GUI**: A special panel to make it easier for players to access this feature.
- 🔐 **Permission-based Security**: Comprehensive permission nodes for all features
- 📝 **Data Persistence**: YAML or MySQL/SQLite database support
- 🎯 **PlaceholderAPI Integration**: Display apartment info in other plugins
- ⚡ **Optimized Performance**: Async operations and caching for minimal server impact
- 🔊 **Rating System**
- 🏷️ **Apartment Display Names**: Owners can set custom names (with color codes) for their apartments.
- 💬 **Welcome Messages**: Custom welcome messages displayed when teleporting to or purchasing an apartment.
- 💰 **Enhanced Rent System**: Rent claim time tracking and new PlaceholderAPI for rent info.
- 💾 **Backup System**: Automatic and manual backup and restore for apartment data.
- ⭐ **Apartment Rating System**: Players can rate apartments (0–10) and see top-rated apartments.
- 🎯 **Full Tab Completion**: Smart tab completion for all commands.
- 🎫 **New PlaceholderAPI Placeholders**: For display name, rating, welcome message, and rent info.
- 📖 **Guest Book System**: Visitors can leave messages; owners can read and clear them using the /ac guestbook command.

## New in v1.2.7
### New Tax System
- **Tax**:
  - Base tax rate = 2.5% of the apartment purchase price.
  - Tax increases by 2.5% per apartment level (e.g., Level 3 = 7.5%).
  - Tax is automatically charged every 24 real-world hours.
  - Tax bills can accumulate.

- **Apartment Status**:
  - **Active**: Normal, the apartment can generate income.
  - **Overdue (3 days without payment)**:
    - Income stops.
    - New tax is 2x the base tax.
  - **Inactive (5 days without payment)**:
    - Apartment cannot be used at all.
    - New tax is 3x the base tax.
  - **Repossession (7 days without payment)**:
    - Ownership is permanently revoked.

- **Payment Mechanism**:
  - `/apartmentcore tax info` → displays all tax bills.
  - `/apartmentcore tax pay` → pays tax arrears.
  - `/apartmentcore tax auto <on/off>` → toggles auto-payment.
  - If auto-payment is active and the balance is sufficient → taxes are automatically paid.
  - If auto-payment fails (insufficient balance) → bills continue to accumulate.

- **Additional Rules**:
  - Apartments with tax arrears cannot be sold.
  - All payments can only be made if there are active bills.

- **Layered Notifications**:
  - New bill: `📢 A new tax bill of X coins has appeared.`
  - Day 2: `⏳ Remember! Tax of X coins must be paid tomorrow.`
  - Day 3 (Overdue): `⚠ Your tax is overdue! The total arrears are now Y coins.`
  - Day 5 (Inactive): `⛔ Your apartment is now Inactive! The total arrears are now Z coins.`
  - Day 7 (Repossession): `❌ Your apartment has been repossessed by the server due to failure to pay taxes.`

- **Expected Output**:
  - System stores tax bills per apartment.
  - System processes status based on time since the last bill.
  - System sends automatic notifications according to the phase.
  - System provides an API for other plugins to check apartment tax status.

---

## Dependencies

### Required
- **Minecraft**: 1.21.4
- **Spigot/Paper**: Latest version for 1.21.4
- **Vault**: Economy API
- **WorldGuard**: Region management

### Optional
- **PlaceholderAPI**: For placeholder support

## Installation

1. Download the ApartmentCore.jar file
2. Place it in your server's `plugins` folder
3. Install required dependencies (Vault, WorldGuard)
4. Restart your server
5. Configure the plugin in `plugins/ApartmentCore/config.yml`

## Commands

### General Commands
| Command | Description | Permission |
|---------|-------------|------------|
| `/apartmentcore info [id]` | View plugin or apartment info | `apartmentcore.use` |
| `/apartmentcore buy <id>` | Buy an apartment | `apartmentcore.buy` |
| `/apartmentcore sell <id>` | Sell your apartment | `apartmentcore.sell` |
| `/apartmentcore confirm` | Confirm pending action | `apartmentcore.confirm` |
| `/apartmentcore teleport <id>` | Teleport to your apartment | `apartmentcore.teleport` |
| `/apartmentcore upgrade <id>` | Upgrade apartment level | `apartmentcore.upgrade` |
| `/apartmentcore list [all/sale/mine/top]` | List apartments | `apartmentcore.list` |
| `/apartmentcore version` | Check plugin version | `apartmentcore.use` |
| `/apartmentcore rate <id> <0-10>` | Rate an apartment | `apartmentcore.use` |

### Guestbook Commands
| Command | Description | Permission |
|---------|-------------|------------|
| `/apartmentcore guestbook leave <id> <msg>` | Leave a message in a guest book | `apartmentcore.leave` |
| `/apartmentcore guestbook read <id>` | Read your guest book messages | `apartmentcore.read` |
| `/apartmentcore guestbook clear <id>` | Clear all messages from your guest book | `apartmentcore.clear` |

### Owner Commands
| Command | Description | Permission |
|---------|-------------|------------|
| `/apartmentcore rent claim <id>` | Claim generated income | `apartmentcore.rent` |
| `/apartmentcore rent info <id>` | View income information | `apartmentcore.rent` |
| `/apartmentcore tax pay <id>` | Pay apartment taxes | `apartmentcore.tax` |
| `/apartmentcore tax info <id>` | View tax information | `apartmentcore.tax` |
| `/apartmentcore setname <id> <n>` | Set apartment display name | `Owner only` |
| `/apartmentcore setwelcome <id> <msg>` | Set welcome message | `Owner only` |
| `/apartmentcore setteleport <id>` | Set teleport location | `apartmentcore.setteleport` |

### Admin Commands
| Command | Description | Permission |
|---------|-------------|------------|
| `/apartmentcore admin create <region> <id> <price> <tax> <days>` | Create apartment | `apartmentcore.admin` |
| `/apartmentcore admin remove <id>` | Remove apartment | `apartmentcore.admin` |
| `/apartmentcore admin set owner <id> <player>` | Change owner | `apartmentcore.admin.set` |
| `/apartmentcore admin set price <id> <amount>` | Change price | `apartmentcore.admin.set` |
| `/apartmentcore admin set rate <id> <value>` | Change rating | `apartmentcore.admin.set` |
| `/apartmentcore admin set tax <id> <amount>` | Change tax | `apartmentcore.admin.set` |
| `/apartmentcore admin set tax_time <id> <days>` | Change tax period | `apartmentcore.admin.set` |
| `/apartmentcore admin set level <id> <level>` | Change level | `apartmentcore.admin.set` |
| `/apartmentcore admin teleport <id>` | Teleport to any apartment | `apartmentcore.admin` |
| `/apartmentcore admin apartment_list` | List all apartments | `apartmentcore.admin` |
| `/apartmentcore admin reload` | Reload configuration | `apartmentcore.admin` |
| `/apartmentcore admin backup create` | 	Create manual backup | `apartmentcore.admin` |
| `/apartmentcore admin backup list` | List all backups | `apartmentcore.admin` |
| `/apartmentcore admin backup restore <file>` | Restore from backup | `apartmentcore.admin` |


## Apartment Levels & Income

| Level | Hourly Income | Upgrade Cost |
|-------|---------------|--------------|
| 1 | $10-100 | - |
| 2 | $100-200 | $1,000 |
| 3 | $200-300 | $2,000 |
| 4 | $300-400 | $3,000 |
| 5 | $400-500 | $5,000 |

## Tax System

- Taxes are collected automatically every configured period (default: 7 days)
- If unable to pay, apartment becomes **inactive**
- Daily penalty of 25% of apartment price during inactive period
- After 3 days of non-payment, apartment becomes available for purchase

## PlaceholderAPI Placeholders

- `%apartmentcore_<id>_owner%` - Apartment owner name
- `%apartmentcore_<id>_price%` - Apartment price
- `%apartmentcore_<id>_tax%` - Tax amount
- `%apartmentcore_<id>_level%` - Apartment level
- `%apartmentcore_<id>_income%` - Pending income
- `%apartmentcore_<id>_status%` - Active/Inactive status
- `%apartmentcore_owned_count%` - Number of apartments owned by player
- `%apartmentcore_total_income%` - Total pending income for player
- `%apartmentcore_<id>_displayname%` - Apartment display name
- `%apartmentcore_<id>_rating%` - Average rating (e.g., "8.5")
- `%apartmentcore_<id>_welcome%` - Welcome message
- `%apartmentcore_last_rent_claim%`- Time since last rent claim
- `%apartmentcore_<id>_tax_due_in%` - Real-time countdown for the next tax payment.
- `%apartmentcore_<id>_income_in%` - Real-time countdown for the next income generation.
- `%apartmentcore_statistic_<id>_total_tax_paid%` - Displays the total tax paid.
- `%apartmentcore_statistic_<id>_total_income_generated%` - Displays the total income generated.
- `%apartmentcore_statistic_<id>_ownership_age_days%` - Displays the age of ownership in days.

## Configuration

Key configuration options in `config.yml`:

```yaml
# Debug mode
debug: false

# Economy settings
economy:
  currency-symbol: "$"
  default-price: 10000
  default-tax: 500
  
# Auto-save
auto-save:
  enabled: true
  interval-minutes: 10
  
# Performance
performance:
  use-async: true
  use-cache: true
```

## Permissions

### Basic Permissions
- `apartmentcore.use` - Basic plugin usage
- `apartmentcore.buy` - Buy apartments
- `apartmentcore.sell` - Sell apartments
- `apartmentcore.teleport` - Teleport to owned apartments
- `apartmentcore.rent` - Manage apartment income
- `apartmentcore.tax` - Pay apartment taxes

### Admin Permissions
- `apartmentcore.admin` - All admin commands
- `apartmentcore.admin.create` - Create apartments
- `apartmentcore.admin.remove` - Remove apartments
- `apartmentcore.admin.set` - Modify apartments
- `apartmentcore.admin.teleport` - Teleport to any apartment
- `apartmentcore.admin.list` - View all apartments
- `apartmentcore.admin.reload` - Reload configuration

### Bypass Permissions
- `apartmentcore.bypass.tax` - Bypass tax payments
- `apartmentcore.bypass.limit` - Bypass ownership limits
- `apartmentcore.bypass.cooldown` - Bypass command cooldowns

## Creating an Apartment (Admin Guide)

1. Create a WorldGuard region for the apartment area
2. Use the create command:
   ```
   /apartmentcore admin create <region_name> <apartment_id> <price> <tax> <tax_days>
   ```
   Example:
   ```
   /apartmentcore admin create apartment_region apt_001 50000 1000 7
   ```

## Troubleshooting

### Plugin won't enable
- Check that all dependencies are installed
- Verify Minecraft version compatibility
- Check console for error messages

### Apartments not generating income
- Verify apartment is active (not inactive due to unpaid taxes)
- Check that the apartment has an owner
- Ensure income generation is enabled in config

### Tax not being collected
- Check tax system is enabled in config
- Verify Minecraft time is progressing normally
- Check player has sufficient funds

## Support

For issues, feature requests, or questions:
- GitHub Issues: [Create an issue](https://github.com/yourusername/ApartmentCore/issues)
- Discord: [Join our server](https://discord.gg/yourserver)

## License

This plugin is proprietary software. All rights reserved.

## Credits

- **Author**: Aithor
- **Version**: 1.2.6
- **Minecraft Version**: 1.21.4

## Changelog

## Version v1.2.6 (2025-09-07)
- Fixed Admin Commands: Three admin set commands that previously did not work have now been fixed and are functioning as intended
- Adding the `/apartmentcore admin set rate <id> <value>` command, which allows administrators to manually set or override the average rating of an apartment.
- Introducing a new statistics tracking system for each apartment owned. Data is stored in a new file called `apartments-stats.yml.` Statistics tracked include: (1) Total Taxes Paid,  (2) Total Revenue Generated, (3) Age of Ownership.
- New PlaceholderAPI support: 
  | Placeholder | Description |
  |--------------|-------------|
  | `%apartmentcore_statistic_<id>_total_tax_paid%` | Displays the total tax paid. |
  | `%apartmentcore_statistic_<id>_total_income_generated%` | Displays the total income generated. |
  | `%apartmentcore_statistic_<id>_ownership_age_days%` | Displays the age of ownership in days. |

- Daily Task Optimization: Daily tasks were refactored to handle tax calculations and ownership age additions more efficiently in a single cycle.
- Data Integrity: The new statistical system has been fully integrated into the auto-save cycle and shutdown procedures to ensure no data is lost.

## Version 1.2.5 (2025-09-06)
- Added Manual Teleport Location system, allowing owners to set a precise teleport point via /ac setteleport.
- Introduced a Guest Book system for visitors to leave messages and for owners to read/clear them (/ac guestbook).
- Implemented Real-time Countdowns for the next tax due date and income generation event, visible in info commands and new placeholders.
- Added new PlaceholderAPI placeholders: %apartmentcore_<id>_tax_due_in% and %apartmentcore_<id>_income_in%.
- Added new commands, permissions, and configuration options to support the new features.
- Created guestbook.yml to handle guest book data persistence.
- bug fixes

## Version 1.2.1 (2025-08-27)
- refactoring the code in the main plugin class into several classes.

### Version 1.2.0 (2025-08-22)
- Added Apartment Display Names feature
- Added customizable Welcome Messages
- Improved Rent System with enhanced tracking
- Introduced Backup System (auto & manual)
- Introduced Apartment Rating System
- Added full tab completion for all commands
- Added new PlaceholderAPI placeholders
- Updated apartments.yml and config.yml with new fields
- Improved error messages and data saving performance
- Fixed tab completion and rent claim tracking bugs

### Version 1.1.0 (20.8.2025)
- Fixed critical NullPointerException and data persistence issues
- Added apartment upgrade system (levels 1-5)
- Implemented confirmation system for important actions
- Added list command with filters
- Fixed Minecraft time calculations for tax system
- Improved teleportation with safe location finding
- Added command cooldown system
- Enhanced PlaceholderAPI support
- Better error handling and input validation
- Performance optimizations and thread safety

### Version 1.0.0 (19.8.2025)
- Core apartment management system
- WorldGuard region integration
- Income generation system
- Tax collection with penalties
- 5-level apartment system
- PlaceholderAPI support
- Admin commands for complete control
- YAML data storage
- Comprehensive permission system
- Optimization for server performance



