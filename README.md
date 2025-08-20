# ApartmentCore Plugin v1.1.0

Advanced apartment management system for Minecraft servers.

## Features

- 🏠 **Apartment Management**: Convert WorldGuard regions into purchasable apartments
- 💰 **Income Generation**: Apartments generate passive income based on their level
- 📊 **Level System**: 5 apartment levels with increasing income rates
- 🏦 **Tax System**: Automatic tax collection with penalty system
- 🔐 **Permission-based Security**: Comprehensive permission nodes for all features
- 📝 **Data Persistence**: YAML or MySQL/SQLite database support
- 🎯 **PlaceholderAPI Integration**: Display apartment info in other plugins
- ⚡ **Optimized Performance**: Async operations and caching for minimal server impact

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

## Building from Source

```bash
git clone https://github.com/yourusername/ApartmentCore.git
cd ApartmentCore
mvn clean package
```

The compiled JAR will be in the `target` folder.

## Commands

### General Commands
| Command | Description | Permission |
|---------|-------------|------------|
| `/apartmentcore info [id]` | View plugin or apartment info | `apartmentcore.use` |
| `/apartmentcore buy <id>` | Buy an apartment | `apartmentcore.buy` |
| `/apartmentcore sell <id>` | Sell your apartment | `apartmentcore.sell` |
| `/apartmentcore teleport <id>` | Teleport to your apartment | `apartmentcore.teleport` |
| `/apartmentcore version` | Check plugin version | `apartmentcore.use` |
| `/apartmentcore list` | Show All Apartment | `apartmentcore.use` |
| `/apartmentcore confirm` | Selling Requirement Action | `apartmentcore.use` |

### Owner Commands
| Command | Description | Permission |
|---------|-------------|------------|
| `/apartmentcore rent claim <id>` | Claim generated income | `apartmentcore.rent` |
| `/apartmentcore rent info <id>` | View income information | `apartmentcore.rent` |
| `/apartmentcore tax pay <id>` | Pay apartment taxes | `apartmentcore.tax` |
| `/apartmentcore tax info <id>` | View tax information | `apartmentcore.tax` |
| `/apartmentcore upgrade` | Upgrade Apartment Action | `apartmentcore.upgrade` |

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
- **Version**: 1.0.0
- **Minecraft Version**: 1.21.4

## Changelog

### Version 1.0.0 (Initial Release)
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
