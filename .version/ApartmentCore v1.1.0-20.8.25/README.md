# ApartmentCore Changelog

## Version 1.1.0 (Major Update)
*Released: 2024*

### 🎯 Major Improvements

#### Core System Enhancements
- **Fixed NullPointerException Issues**: Resolved critical startup errors by properly initializing data files before dependency checks
- **Minecraft Time System**: Completely reworked time calculations to use proper Minecraft time instead of real-world time
- **Tax System Overhaul**: Fixed tax collection to properly track Minecraft days and process taxes accordingly
- **Memory Optimization**: Added proper null checks and error handling throughout the codebase

#### New Features
- **Apartment Upgrade System**: Players can now upgrade apartments from level 1 to 5
    - Command: `/apartmentcore upgrade <apartment_id>`
    - Each level increases income generation
    - Upgrade costs are configurable

- **Confirmation System**: Added safety confirmations for important actions
    - Selling apartments now requires confirmation
    - Command: `/apartmentcore confirm`
    - 30-second timeout for confirmations

- **List Command**: New listing functionality with filters
    - `/apartmentcore list all` - Show all apartments
    - `/apartmentcore list sale` - Show apartments for sale
    - `/apartmentcore list mine` - Show owned apartments

- **Command Cooldown System**: Prevents command spam
    - Configurable cooldown period
    - Bypass permission: `apartmentcore.bypass.cooldown`

- **Enhanced PlaceholderAPI Support**:
    - `%apartmentcore_owned_count%` - Number of apartments owned
    - `%apartmentcore_total_income%` - Total pending income
    - Fixed registration timing issues

### 🐛 Bug Fixes

#### Critical Fixes
1. **Data Persistence**: Fixed data not saving properly on server shutdown
2. **Tax Calculation**: Corrected Minecraft day tracking for accurate tax collection
3. **Income Generation**: Fixed random income generation within proper ranges
4. **Region Teleportation**: Improved teleport location calculation to find safe spots
5. **Permission Checks**: Added missing permission validations for all commands
6. **WorldGuard Integration**: Fixed player addition/removal from regions

#### Minor Fixes
- Fixed version placeholder showing `${project.version}` instead of actual version
- Corrected apartment limit checking for players
- Fixed penalty calculations using proper percentages
- Improved error messages for better user feedback
- Fixed concurrent modification exceptions in collections

### 🔧 Technical Improvements

#### Code Quality
- **Error Handling**: Comprehensive try-catch blocks for all critical operations
- **Input Validation**: Added validation for all numeric inputs
- **Null Safety**: Extensive null checks to prevent NPEs
- **Thread Safety**: Using ConcurrentHashMap for thread-safe operations
- **Resource Management**: Proper cleanup of tasks on disable

#### Performance
- **Optimized Schedulers**: Reduced task frequency for better performance
- **Efficient Day Tracking**: Only check for day changes every 5 seconds
- **Memory Management**: Proper cleanup of expired confirmations
- **Database Optimization**: Prepared for future MySQL implementation

### 📋 Configuration Updates

#### New Configuration Options
```yaml
# Command cooldown in milliseconds
security:
  command-cooldown: 1000

# Grace period for inactive apartments
time:
  inactive-grace-period: 3

# Economy percentages
economy:
  sell-percentage: 70
  penalty-percentage: 25
```

### 🔐 Security Enhancements

- **Permission System**: Complete permission node coverage
- **Input Sanitization**: All user inputs are validated
- **Command Cooldowns**: Prevents abuse and server stress
- **Confirmation System**: Protects against accidental actions
- **Admin Logging**: All admin actions are logged

### 📊 Data Structure Improvements

#### Apartment Class Enhancements
- Added `inactiveSince` field for tracking inactive duration
- Added `lastTaxCheckDay` for accurate tax tracking
- Improved serialization/deserialization

#### New Classes
- `LevelConfig`: Manages apartment level configurations
- `ConfirmationAction`: Handles pending confirmations

### 🎮 User Experience

#### Command Improvements
- Better error messages with specific guidance
- Color-coded status indicators
- Formatted money displays with currency symbol
- Clear feedback for all actions

#### Quality of Life
- Auto-save functionality with configurable intervals
- Detailed apartment information displays
- Warning messages for pending apartment loss
- Grace period notifications

### 📝 Documentation

- **JavaDoc**: Complete documentation for all methods
- **Comments**: Inline comments for complex logic
- **README**: Updated with new commands and features
- **Config**: Detailed configuration file comments

### ⚠️ Known Issues

- PlaceholderAPI may need server restart to register properly
- Large apartment counts (1000+) may impact performance
- MySQL implementation pending for large servers

### 🔄 Migration Notes

**From v1.0.0 to v1.1.0:**
1. Backup your `apartments.yml` file
2. Update the plugin JAR
3. Let the plugin regenerate config.yml
4. Restart the server
5. Existing apartments will be automatically migrated

### 🚀 Future Plans

- MySQL/SQLite database support
- Player-to-player apartment trading
- Apartment auctions system
- GUI interface for apartment management
- Region visualization
- Rent collection scheduling
- Multi-world support improvements

---

## Version 1.0.0 (Initial Release)
*Released: 2024*

### Features
- Basic apartment management system
- WorldGuard region integration
- Vault economy support
- Tax collection system
- Income generation
- PlaceholderAPI support
- Admin commands
- YAML data storage

### Commands
- Basic buy/sell functionality
- Teleportation system
- Tax payment
- Income claiming
- Admin management

### Known Issues
- NullPointerException on startup
- Time calculations using real-world time
- Missing upgrade functionality
- No confirmation system

---

## Installation Requirements

### Dependencies
- **Minecraft**: 1.21.4
- **Spigot/Paper**: Latest version
- **Vault**: Required
- **WorldGuard**: Required
- **PlaceholderAPI**: Optional

### Permissions
See plugin.yml for complete permission nodes

---

## Support

For issues or feature requests:
- Report bugs with full error logs
- Include server version information
- Describe steps to reproduce

---

## Credits

**Developer**: Aithor  
**Version**: 1.1.0  
**License**: Proprietary

Special thanks to the Minecraft plugin development community for testing and feedback.
