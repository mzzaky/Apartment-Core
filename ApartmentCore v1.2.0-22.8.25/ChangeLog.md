# 🎉 ApartmentCore v1.2.0 Update Guide

## 📋 New Features Overview

Version 1.2.0 brings exciting new features to enhance apartment management and user experience!

---

## ✨ New Features

### 1. **Apartment Display Names** 🏷️
Players can now customize apartment display names!

**Command:**
```
/apartmentcore setname <apartment_id> <display_name>
```

**Features:**
- Maximum 32 characters
- Supports color codes with `&`
- Shows in all apartment listings
- Preserved when apartment changes hands

**Example:**
```
/apartmentcore setname apt001 &6Golden &bPalace
```

---

### 2. **Welcome Messages** 💬
Set custom welcome messages for your apartments!

**Command:**
```
/apartmentcore setwelcome <apartment_id> <message>
```

**Features:**
- Maximum 100 characters
- Supports color codes with `&`
- Shows when teleporting to apartment
- Shows when purchasing apartment
- Use "none" or "clear" to remove

**Example:**
```
/apartmentcore setwelcome apt001 &aWelcome to paradise! Enjoy your stay!
```

---

### 3. **Enhanced Rent System** 💰
Improved rent tracking with time information!

**New Features:**
- Track last rent claim time
- See time since last claim
- New placeholder: `%apartmentcore_last_rent_claim%`

**Rent Info Display:**
```
Pending Income: $250.00
Hourly Income Range: $10.00 - $100.00
Level: 2/5
Last claim: 5 minutes ago
Status: Active
```

---

### 4. **Backup System** 💾
Comprehensive backup and restore functionality!

**Commands:**
```
/apartmentcore admin backup create     # Create manual backup
/apartmentcore admin backup list       # List all backups
/apartmentcore admin backup restore <filename>  # Restore from backup
```

**Features:**
- Automatic hourly backups
- Backup on server shutdown
- Keep up to 10 backups (configurable)
- Manual backup creation
- Easy restore with pre-restore backup

**Configuration:**
```yaml
backup:
  enabled: true
  max-backups: 10
  backup-on-shutdown: true
```

---

### 5. **Rating System** ⭐
Players can now rate apartments!

**Command:**
```
/apartmentcore rate <apartment_id> <0-10>
```

**Features:**
- Rate apartments from 0 to 10
- 24-hour cooldown per apartment
- Only active apartments can be rated
- Ratings reset when apartment is sold
- Average rating shown in info and lists
- Top rated apartments list

**View Top Rated:**
```
/apartmentcore list top
```

**Rating Display:**
```
Rating: 8.5/10.0 (15 reviews)
```

---

### 6. **Tab Completion** 🎯
Full tab completion for all commands!

**Supported Completions:**
- Main commands
- Sub-commands
- Apartment IDs
- Admin commands
- Filter options
- Rating values

**Smart Context:**
- Only shows relevant options
- Filters by permission
- Partial name matching
- Dynamic apartment ID suggestions

---

## 📝 New Commands Summary

| Command | Description | Permission |
|---------|-------------|------------|
| `/apartmentcore setname <id> <n>` | Set apartment display name | Owner only |
| `/apartmentcore setwelcome <id> <msg>` | Set welcome message | Owner only |
| `/apartmentcore rate <id> <0-10>` | Rate an apartment | `apartmentcore.use` |
| `/apartmentcore list top` | Show top rated apartments | `apartmentcore.list` |
| `/apartmentcore admin backup create` | Create manual backup | `apartmentcore.admin` |
| `/apartmentcore admin backup list` | List all backups | `apartmentcore.admin` |
| `/apartmentcore admin backup restore <file>` | Restore from backup | `apartmentcore.admin` |

---

## 🔄 New Placeholders

### PlaceholderAPI Support
```
%apartmentcore_<id>_displayname%  - Apartment display name
%apartmentcore_<id>_rating%       - Average rating (e.g., "8.5")
%apartmentcore_<id>_welcome%      - Welcome message
%apartmentcore_last_rent_claim%   - Time since last rent claim
```

---

## 📊 Data Storage Changes

### New Fields in apartments.yml
```yaml
apartments:
  apartment_id:
    # Existing fields...
    display-name: "Custom Name"
    welcome-message: "Welcome message here"

ratings:
  apartment_id:
    total: 85.5
    count: 10
    raters:
      uuid-1: 8.5
      uuid-2: 9.0
```

---

## 🔧 Configuration Updates

### New in config.yml
```yaml
# Backup Settings (NEW)
backup:
  enabled: true
  max-backups: 10
  backup-on-shutdown: true
```

---

## 🚀 Installation/Update Instructions

### For Existing Servers (Upgrading from v1.1.0)

1. **Backup Your Data**
   ```bash
   cp -r plugins/ApartmentCore plugins/ApartmentCore_backup_v110
   ```

2. **Replace Plugin JAR**
   ```bash
   # Stop server
   stop
   
   # Replace JAR
   rm plugins/ApartmentCore-1.1.0.jar
   cp ApartmentCore-1.2.0.jar plugins/
   ```

3. **Start Server**
   ```bash
   # Start server
   start
   
   # Verify version
   /apartmentcore version
   # Should show: ApartmentCore version 1.2.0
   ```

4. **Check New Features**
   ```
   # Test tab completion
   /apartmentcore [TAB]
   
   # Create first backup
   /apartmentcore admin backup create
   ```

### For New Installations

Follow standard installation guide with ApartmentCore-1.2.0.jar

---

## ✅ Testing Checklist

### Basic Tests
- [ ] Plugin loads without errors
- [ ] Version shows 1.2.0
- [ ] Existing apartments still work
- [ ] Tab completion works

### New Feature Tests
- [ ] Set apartment display name
- [ ] Set welcome message
- [ ] Rate an apartment
- [ ] View top rated list
- [ ] Create manual backup
- [ ] List backups
- [ ] Test restore (on test server)

### Integration Tests
- [ ] Placeholders work
- [ ] Rent time tracking works
- [ ] Ratings persist across restarts
- [ ] Backups created automatically

---

## ⚠️ Important Notes

### Backward Compatibility
- ✅ Fully compatible with v1.1.0 data
- ✅ Automatic migration of existing apartments
- ✅ No data loss during upgrade

### Performance Impact
- Minimal impact from new features
- Backup system runs asynchronously
- Rating system uses efficient storage

### Known Limitations
- Display names limited to 32 characters
- Welcome messages limited to 100 characters
- Ratings have 24-hour cooldown
- Maximum 10 backups by default

---

## 🐛 Bug Fixes in v1.2.0

1. **Fixed:** Tab completion not working
2. **Fixed:** Rent claim time not tracking properly
3. **Improved:** Better error messages
4. **Improved:** More efficient data saving

---

## 📚 Usage Examples

### Setting Up a Premium Apartment
```bash
# Create apartment
/apartmentcore admin create luxury_region lux001 100000 5000 30

# Set custom name
/apartmentcore setname lux001 &6★ &bLuxury &6Penthouse &6★

# Set welcome message
/apartmentcore setwelcome lux001 &aWelcome to the finest apartment in the city!

# Check info
/apartmentcore info lux001
```

### Managing Backups
```bash
# Create backup before major changes
/apartmentcore admin backup create

# List available backups
/apartmentcore admin backup list

# Restore if needed
/apartmentcore admin backup restore apartments_manual_2024-01-15_10-30-00.yml
```

### Rating Apartments
```bash
# Rate an apartment
/apartmentcore rate lux001 9.5

# View top rated
/apartmentcore list top
```

---

## 🎮 Player Guide

### For Apartment Owners
1. **Customize Your Apartment**
   - Give it a unique name with colors
   - Set a welcoming message
   - Keep it active for good ratings

2. **Maintain Good Ratings**
   - Keep apartment active
   - Respond to feedback
   - Upgrade regularly

3. **Track Your Income**
   - Check rent info for claim times
   - Monitor pending income
   - Claim regularly

### For Apartment Seekers
1. **Check Ratings**
   - View top rated apartments
   - Read display names
   - Consider ratings before buying

2. **Rate Fairly**
   - Rate apartments you visit
   - Be honest with ratings
   - Help others make decisions

---

## 🆘 Troubleshooting

### Issue: Tab completion not working
**Solution:** Restart server (not reload)

### Issue: Backups not creating
**Solution:** Check permissions on plugins/ApartmentCore/backups/ folder

### Issue: Ratings not saving
**Solution:** Check if apartment is active (inactive apartments can't be rated)

### Issue: Display name not showing colors
**Solution:** Use `&` for color codes, not `§`

---

## 📞 Support

For issues with v1.2.0:
1. Enable debug mode in config
2. Reproduce the issue
3. Check console for errors
4. Report with full error log

---

## 🎉 Enjoy the Update!

ApartmentCore v1.2.0 brings quality-of-life improvements that make apartment management more engaging and user-friendly. The rating system adds a competitive element, while display names and welcome messages allow for personalization. The backup system ensures your data is always safe!

**Thank you for using ApartmentCore!**

---

*Version 1.2.0 Update Guide*  
*Released: 2024*  
*Developer: Aithor*
