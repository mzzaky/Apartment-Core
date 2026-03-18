import re

with open('.html/shop.html', 'r', encoding='utf-8') as f:
    html = f.read()

# Replace body background gradient
html = re.sub(r'linear-gradient\(135deg, #0a0c10 0%, #111316 50%, #1a1d23 100%\)', '#111316', html)

# Replace other background linear gradients with appropriate solid colors
html = re.sub(r'linear-gradient\(135deg, #ffd966[^)]+\)', '#ffd966', html)
html = re.sub(r'linear-gradient[^;]+rgba\(255, 217, 102, 0\.1[^;]+', 'rgba(255, 217, 102, 0.1)', html)
html = re.sub(r'linear-gradient\([^;]+var[^;]+', 'transparent', html)

# Fallback for any remaining linear gradients
html = re.sub(r'background:\s*linear-gradient\([^;]+;', 'background: transparent;', html)
html = re.sub(r'background:\s*radial-gradient\([^;]+;', 'background: transparent;', html)

# Add Vault Expansion
vault_html = """                    <div class="shop-item-card">
                        <span class="shop-item-icon">📦</span>
                        <h4>Vault Expansion</h4>
                        <div class="shop-item-effect">Capacity Bonus: 2.5% - 12.5%</div>
                        <div class="shop-item-details">
                            Increases your apartment's maximum income capacity. Essential for storing more pending income.
                        </div>
                        <div class="shop-item-pricing">
                            <span class="price-range">$6,000 - $60,000</span>
                            <span class="tier-badge">5 Tiers</span>
                        </div>
                    </div>
                </div>"""

# Replace 5 Categories with 6 Categories
html = html.replace('<span class="section-badge">5 Categories</span>', '<span class="section-badge">6 Categories</span>')
# Insert vault expansion card right before the end of the grid
html = html.replace('</div>\n            </section>\n\n            <section class="content-section" id="tier-system">', vault_html + '\n            </section>\n\n            <section class="content-section" id="tier-system">')

with open('.html/shop.html', 'w', encoding='utf-8') as f:
    f.write(html)

print("Optimization complete.")
