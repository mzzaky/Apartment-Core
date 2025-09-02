# ApartmentCore Plugin v1.2.5

Advanced apartment management system for Minecraft servers with comprehensive features and optimizations.

## Features

- 🏠 **Apartment Management**: Convert WorldGuard regions into purchasable apartments
- 💰 **Income Generation**: Apartments generate passive income based on their level
- 📊 **Level System**: 5 apartment levels with increasing income rates
- 🏦 **Tax System**: Automatic tax collection with penalty system
- 🏷️ **Apartment Display Names**: Owners can set custom names with color codes
- 💬 **Welcome Messages**: Custom messages when entering apartments
- 📝 **Guest Book System**: Visitors can leave messages for apartment owners
- 📍 **Custom Teleport Points**: Owners can set custom teleport locations
- ⏱️ **Tax & Income Timers**: Track when taxes are due and income will be generated
- 📈 **Statistics System**: Comprehensive tracking of apartment metrics
- 🔁 **Buy & Sell**: Players can resell their apartments for up to 70% of price
- 🌐 **Fast Travel**: Instant teleportation to owned apartments
- 💎 **Custom GUI**: Special panel for easier feature access
- 🔐 **Permission-based Security**: Comprehensive permission nodes
- 💾 **Data Persistence**: YAML or MySQL/SQLite database support
- 🎯 **PlaceholderAPI Integration**: Display apartment info in other plugins
- ⚡ **Optimized Performance**: Async operations and caching
- ⭐ **Rating System**: Players can rate apartments (0-10)
- 🔄 **Backup System**: Automatic and manual backup/restore

### New in v1.2.5

- 📍 **Custom Teleport Locations**: Apartment owners can set specific teleport points within their apartment using `/apartmentcore setteleport <id>`
- 📝 **Guest Book System**: Command-based messaging system where visitors can leave messages for apartment owners
- ⏱️ **Tax & Income Countdown**: Real-time countdown timers showing when taxes are due and when income will be generated
- 📊 **Enhanced Statistics**: Comprehensive apartment statistics with PlaceholderAPI support
- 🔧 **Improved Configuration**: Statistics settings now configurable in config.yml

# KNOWN ISSUES
- Command `/apartmentcore setteleport <id>` tab completion does not appear
- Incorrect tab completion for the command `/apartmentcore tax claim <id>`, which should be `/apartmentcore tax pay <id>`
- Subcommand tab completion for the command `/apartmentcore guestbook` does not appear, which should be like this: `/apartmentcore guestbook write|read|clear <id>`
- All statistics placeholders return empty responses when used.
- The placeholder `%apartmentcore_<id>_tax_countdown%` does not provide the correct response. It should provide a countdown in minutes until the tax bill is due.
- the placeholder `%apartmentcore_<id>_income_countdown%` does not provide the correct response. It should provide a countdown in minutes until the income is generated.

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

### Owner Commands
| Command | Description | Permission |
|---------|-------------|------------|
| `/apartmentcore rent claim <id>` | Claim generated income | `apartmentcore.rent` |
| `/apartmentcore rent info <id>` | View income information | `apartmentcore.rent` |
| `/apartmentcore tax pay <id>` | Pay apartment taxes | `apartmentcore.tax` |
| `/apartmentcore tax info <id>` | View tax information | `apartmentcore.tax` |
| `/apartmentcore setname <id> <name>` | Set apartment display name | `Owner only` |
| `/apartmentcore setwelcome <id> <msg>` | Set welcome message | `Owner only` |
| `/apartmentcore setteleport <id>` | Set custom teleport location | `Owner only` |

### Guest Book Commands (v1.2.5)
| Command | Description | Permission |
|---------|-------------|------------|
| `/apartmentcore guestbook write <id> <msg>` | Leave a message | `apartmentcore.use` |
| `/apartmentcore guestbook read <id>` | Read guest book messages | `Owner only` |
| `/apartmentcore guestbook clear <id>` | Clear all messages | `Owner only` |

### Admin Commands
| Command | Description | Permission |
|---------|-------------|------------|
| `/apartmentcore admin create <region> <id> <price> <tax> <days>` | Create apartment | `apartmentcore.admin` |
| `/apartmentcore admin remove <id>` | Remove apartment | `apartmentcore.admin` |
| `/apartmentcore admin set owner <id> <player>` | Change owner | `apartmentcore.admin` |
| `/apartmentcore admin set price <id> <amount>` | Change price | `apartmentcore.admin` |
| `/apartmentcore admin set tax <id> <amount>` | Change tax | `apartmentcore.admin` |
| `/apartmentcore admin set tax_time <id> <days>` | Change tax period | `apartmentcore.admin` |
| `/apartmentcore admin set level <id> <level>` | Change level | `apartmentcore.admin` |
| `/apartmentcore admin teleport <id>` | Teleport to any apartment | `apartmentcore.admin` |
| `/apartmentcore admin apartment_list` | List all apartments | `apartmentcore.admin` |
| `/apartmentcore admin reload` | Reload configuration | `apartmentcore.admin` |
| `/apartmentcore admin backup create` | Create manual backup | `apartmentcore.admin` |
| `/apartmentcore admin backup list` | List all backups | `apartmentcore.admin` |
| `/apartmentcore admin backup restore <file>` | Restore from backup | `apartmentcore.admin` |
| `/apartmentcore admin statistics` | View global statistics | `apartmentcore.admin` |

## PlaceholderAPI Placeholders

### Basic Placeholders
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
- `%apartmentcore_last_rent_claim%` - Time since last rent claim

### Timer Placeholders (v1.2.5)
- `%apartmentcore_<id>_tax_countdown%` - Days until next tax payment
- `%apartmentcore_<id>_income_countdown%` - Minutes until next income generation
- `%apartmentcore_<id>_guestbook_count%` - Number of unread messages

### Statistics Placeholders (v1.2.5)
- `%apartmentcore_<id>_statistic_visits%` - Total visits to apartment
- `%apartmentcore_<id>_statistic_total_income%` - Total income generated
- `%apartmentcore_<id>_statistic_total_taxes%` - Total taxes paid
- `%apartmentcore_<id>_statistic_owner_count%` - Total number of owners
- `%apartmentcore_<id>_statistic_age_days%` - Days since apartment creation
- `%apartmentcore_global_statistic_total_sold%` - Total apartments sold globally
- `%apartmentcore_global_statistic_total_income%` - Total income generated globally
- `%apartmentcore_global_statistic_highest_sale%` - Highest apartment sale price
- `%apartmentcore_global_statistic_most_popular%` - Most visited apartment ID

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
- Tax countdown timer shows days remaining until next payment

## Guest Book System (v1.2.5)

The guest book system allows visitors to leave messages for apartment owners:
- Visitors can write messages up to 200 characters
- Owners receive notifications when online
- Maximum 50 messages per apartment
- Support for color codes in messages (if enabled)
- Messages display sender name and timestamp

## Statistics Tracking (v1.2.5)

The plugin tracks comprehensive statistics including:
- **Per Apartment**: Total owners, income generated, taxes paid, visits, upgrades
- **Global**: Total sales, transactions, highest sale price, most popular apartment
- All statistics are configurable and accessible via PlaceholderAPI

## Creating an Apartment (Admin Guide)

1. Create a WorldGuard region for the apartment area
2. Use the create command:
`/apartmentcore admin create <region_name> <apartment_id> <price> <tax> <tax_days>`
Example
`/apartmentcore admin create apartment_region apt_001 50000 1000 7`

## Changelog

### Version 1.2.5 (2025-08-27)
- Added Custom Teleport Locations feature for apartment owners
- Introduced Guest Book System with command-based messaging
- Added Tax & Income countdown timers with PlaceholderAPI support
- Implemented comprehensive Statistics System with configurable tracking
- Added new PlaceholderAPI placeholders for timers and statistics
- Improved data structure for better performance
- Enhanced configuration with new statistics and guest book settings

### Version 1.2.1 (2025-08-25)
- Refactored main class into several manager classes
- Improved code organization and maintainability

### Version 1.2.0 (2025-08-22)
- Added Apartment Display Names feature
- Added customizable Welcome Messages
- Improved Rent System with enhanced tracking
- Introduced Backup System (auto & manual)
- Introduced Apartment Rating System
- Added full tab completion for all commands
- Added new PlaceholderAPI placeholders

### Version 1.1.0 (2025-08-20)
- Fixed critical NullPointerException and data persistence issues
- Added apartment upgrade system (levels 1-5)
- Implemented confirmation system for important actions
- Added list command with filters
- Fixed Minecraft time calculations for tax system

### Version 1.0.0 (2025-08-19)
- Core apartment management system
- WorldGuard region integration
- Income generation system
- Tax collection with penalties
- 5-level apartment system

## Support

For issues, feature requests, or questions:
- GitHub Issues: [Create an issue](https://github.com/yourusername/ApartmentCore/issues)
- Discord: [Join our server](https://discord.gg/yourserver)

## License

This plugin is proprietary software. All rights reserved.

## Credits

- **Author**: Aithor
- **Version**: 1.2.5
- **Minecraft Version**: 1.21.4
