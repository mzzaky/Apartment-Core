# Apartment Core Plugin - Tax System Documentation

## Overview
The Apartment Core plugin implements a comprehensive tax system for Minecraft apartments using a modern invoice-based approach. This system replaces the legacy flat tax system and provides dynamic taxation with progressive penalties and automated notifications.

## Core Components

### 1. TaxInvoice Class
**Location:** `src/main/java/com/aithor/apartmentcorei3/TaxInvoice.java`

#### Key Properties:
- `id`: Unique UUID string identifier
- `amount`: Tax amount to be paid (double)
- `createdAt`: Creation timestamp in epoch milliseconds
- `dueAt`: Due date timestamp (createdAt + 3 days)
- `paidAt`: Payment timestamp (0 = unpaid)
- `notifNewSent`: Flag to prevent spam notifications for new invoices
- `notifDay2Sent`: Flag for 1-day-before-due reminder
- `notifDay3Sent`: Flag for overdue notification
- `notifDay5Sent`: Flag for inactive status notification

#### Key Methods:
- `isPaid()`: Returns true if paidAt > 0
- `ageMillis(long now)`: Calculates age in milliseconds since creation
- `daysSinceCreated(long now)`: Calculates days since creation
- `daysOverdue(long now)`: Calculates days past due date
- `serialize()`: Converts to Map for YAML storage
- `deserialize(Map data)`: Reconstructs from stored data

### 2. TaxStatus Enum
**Location:** `src/main/java/com/aithor/apartmentcorei3/TaxStatus.java`

#### Status Levels:
- `ACTIVE`: Normal operation, can generate income
- `OVERDUE`: 3+ days without payment, income generation stops
- `INACTIVE`: 5+ days without payment, apartment becomes unusable
- `REPOSSESSION`: 7+ days without payment, ownership is permanently removed

### 3. Apartment Class Integration
**Location:** `src/main/java/com/aithor/apartmentcorei3/Apartment.java`

#### New Tax Properties:
- `taxInvoices`: List of all tax invoices (active and paid)
- `autoTaxPayment`: Per-apartment auto-payment flag
- `lastInvoiceAt`: Timestamp of last invoice creation

#### Tax Calculation Methods:
- `getBaseTaxPercent()`: Returns 2.5% per level (Level 3 = 7.5%)
- `computeBaseTaxAmount()`: Calculates base tax = price × baseTaxPercent
- `getTotalUnpaid()`: Sums all unpaid invoice amounts
- `computeTaxStatus(long now)`: Determines current tax status based on oldest unpaid invoice
- `canGenerateIncome(long now)`: Returns true if status is ACTIVE

#### Core Tax Processing:
- `tickTaxInvoices()`: Main processing method called daily
  - Generates new invoices every 24 hours real-time
  - Applies multipliers: 1x (normal), 2x (overdue), 3x (inactive)
  - Handles auto-payment if enabled and funds available
  - Sends progressive notifications
  - Applies status effects and repossession

## System Flow

### 1. Invoice Generation
- Triggered every 24 hours real-world time via `TaskManager.startDailyUpdateTask()`
- Called through `ApartmentManager.processDailyUpdates()` → `Apartment.tickTaxInvoices()`
- Base amount = apartment price × (0.025 × level)
- Multiplier applied based on current tax status:
  - ACTIVE: 1x
  - OVERDUE: 2x
  - INACTIVE: 3x
- Due date = creation time + 3 days

### 2. Status Determination
- Based on oldest unpaid invoice age:
  - 0-2 days: ACTIVE
  - 3-4 days: OVERDUE
  - 5-6 days: INACTIVE
  - 7+ days: REPOSSESSION

### 3. Notification System
Progressive notifications prevent spam:
- **New Invoice**: Immediate notification when created
- **Day 2 Reminder**: "Tax due tomorrow" (1 day before due)
- **Day 3 Overdue**: "Taxes are overdue" notification
- **Day 5 Inactive**: "Apartment now Inactive" notification

### 4. Auto-Payment System
- Per-apartment toggle via `/apartmentcore tax auto <on|off>`
- Attempts payment immediately when invoice is created
- Processes existing unpaid invoices oldest-first
- Stops if insufficient funds for next invoice
- Updates player balance and apartment stats

### 5. Repossession Process
At day 7:
- Ownership permanently removed
- All data reset (income, ratings, guestbook, stats)
- Custom teleport location cleared
- Player receives notification

## Command Interface

### Player Commands
**Location:** `src/main/java/com/aithor/apartmentcorei3/ApartmentCommandService.java`

#### `/apartmentcore tax info`
- Displays tax status for all owned apartments
- Shows active invoice count and total arrears
- Lists individual unpaid invoices with age and due status

#### `/apartmentcore tax pay`
- Pays all unpaid invoices across all apartments
- Processes oldest invoices first
- Requires sufficient funds for total amount
- Updates stats and clears legacy inactive flags

#### `/apartmentcore tax auto <on|off>`
- Toggles auto-payment for all owned apartments
- Global setting per player account

### Admin Commands
#### `/apartmentcore admin set tax <id> <amount>`
- Manually sets legacy tax amount (for backward compatibility)
- Does not affect new invoice system

## Data Persistence

### Storage Structure
**YAML Configuration Files:**
- `apartments.yml`: Main apartment data including tax invoices
- `apartments-stats.yml`: Tax payment statistics

### Invoice Storage Format:
```yaml
apartments:
  apartment_id:
    tax-invoices:
    - id: "uuid-string"
      amount: 100.0
      createdAt: 1640995200000
      dueAt: 1641254400000
      paidAt: 1641081600000
      notifNewSent: true
      notifDay2Sent: true
      notifDay3Sent: false
      notifDay5Sent: false
```

### Loading Process
**Location:** `src/main/java/com/aithor/apartmentcorei3/ApartmentManager.java`
- `loadApartments()`: Deserializes tax invoice data
- Backward compatibility with legacy tax fields
- Error handling for malformed invoice data

## Integration Points

### Economy Integration
- Uses Vault economy API for payments
- Withdraws funds for tax payments
- Deposits refunds on apartment removal

### Task Scheduling
**Location:** `src/main/java/com/aithor/apartmentcorei3/TaskManager.java`
- `startDailyUpdateTask()`: Runs every 100 ticks (5 seconds)
- Checks for Minecraft day changes (every 24 hours in-game)
- Triggers tax processing via `ApartmentManager.processDailyUpdates()`

### Statistics Tracking
**Location:** `src/main/java/com/aithor/apartmentcorei3/ApartmentStats.java`
- Tracks total tax paid per apartment
- Updated on successful payments
- Persisted to `apartments-stats.yml`

### Placeholder Integration
**Location:** `src/main/java/com/aithor/apartmentcorei3/ApartmentPlaceholder.java`
- `%apartmentcore_tax_status%`: Current tax status
- `%apartmentcore_next_invoice_in%`: Time until next invoice
- Legacy placeholders maintained for compatibility

## Technical Implementation Details

### Time Calculations
- All timestamps use `System.currentTimeMillis()` (epoch milliseconds)
- Day calculations: `age / 86_400_000L` (24 hours in milliseconds)
- Real-time processing (not Minecraft time-dependent except for daily triggers)

### Thread Safety
- Uses `ConcurrentHashMap` for apartment storage
- Tax processing runs on main thread for Bukkit API compatibility
- Auto-save operations are synchronous

### Backward Compatibility
- Legacy `tax` and `taxDays` fields preserved in storage
- Old tax system logic bypassed in favor of invoice system
- Migration handled automatically on first load

### Error Handling
- Malformed invoice data logged and skipped
- Invalid UUIDs in ratings handled gracefully
- Economy transaction failures logged but don't crash system

## Configuration Dependencies

### Level Configuration
**Location:** `src/main/resources/config.yml`
- Defines income ranges per level
- Used in tax percentage calculations
- Configurable via `ConfigManager.getLevelConfig()`

### Message Configuration
**Location:** `src/main/resources/messages.yml`
- Tax-related notification messages
- Configurable colors and formatting

## Performance Considerations

### Memory Usage
- Invoice lists grow over time (no automatic cleanup)
- Only unpaid invoices actively processed
- Serialization includes all historical invoices

### Processing Load
- Daily processing scales with apartment count
- Notification checks run on every tick (100 ticks = 5 seconds)
- Auto-payment attempts only when enabled

### Database Operations
- Full apartment data saved on auto-save intervals
- Tax invoice data included in main apartment serialization
- Separate stats file for performance isolation

## Future Enhancement Opportunities

### Potential Improvements:
1. **Invoice Cleanup**: Automatic removal of old paid invoices
2. **Tax Amnesty**: Administrative commands for tax forgiveness
3. **Progressive Taxation**: More complex tax calculation formulas
4. **Tax History**: Detailed payment history tracking
5. **Multi-Currency**: Support for different economy plugins
6. **Tax Incentives**: Bonuses for consistent payment history

### Monitoring and Analytics:
- Tax collection efficiency metrics
- Player payment behavior analysis
- Revenue forecasting based on apartment values

This documentation provides a comprehensive technical overview of the tax system implementation. The system is designed to be robust, scalable, and maintainable while providing a realistic economic simulation for the Minecraft apartment economy.