##Summary of Changes:

1. **Quality of Life**:
    - All relevant files (`plugin.yml`, `ApartmentCorei3.java`, etc.) have been updated to version 1.2.5.
    - changes to the code logic in several features that were already present in previous versions.
    - fixing several commands where tab completion does not work correctly

2. **Manual Teleport Location**:
    - A new command, `/apartmentcore setteleport`, allows owners to set the exact teleport point for their apartment.
    - The Apartment data model now stores this custom location.
    - The teleport logic prioritizes this custom location, falling back to the region's center if not set.

3. **Guest Book System**:
    - Players can now leave messages for apartment owners using `/apartmentcore guestbook leave <id> <message>`.
    - Owners can read and clear their guestbook with `/apartmentcore guestbook read <id>` and `/apartmentcore guestbook clear <id>`.
    - Guestbook data is stored in a new `guestbook.yml` file.
    - New configuration options for message limits and cooldowns have been added to `config.yml`.

4. **Real-Time Countdowns**:
    - The output of `/ac info`, `/ac rent info`, and `/ac tax info` now includes real-time countdowns for the next income deposit and next tax due date.
    - New PlaceholderAPI placeholders have been added:
        - `%apartmentcore_<id>_tax_due_in%`
        - `%apartmentcore_<id>_income_in%`

5. NewCode & Configuration Enhancements:
    - New permissions for the added features have been included in `plugin.yml`.
    - `config.yml` has a new guestbook section for customization.
    - Command handlers and tab-completion have been updated for the new commands.