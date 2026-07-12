# Monetization — what we may sell, and what we must never sell

> ⚠️ Not legal advice. Mojang updates its rules; the
> [Minecraft Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines)
> are the only text that counts. Re-read them before opening a store.

---

## The rule that decides everything

**Mojang forbids selling any gameplay advantage** between paying and non-paying players.
A server that breaks this can be **blacklisted** — nobody can connect to it any more.

For a **ranked PvP server**, this is not just a legal constraint, it is a product constraint:
**a ladder that can be bought is worth nothing.** The value of an ELO rating is its
credibility. Pay-to-win would destroy the very thing players are here for.

Mojang did remove the old clause saying revenue should only "cover costs" — running a real
business is allowed, **within the rules**.

### The one-line rule we design around

> **Everything that affects combat stays free. We only sell what is *seen*.**

| ❌ Never sell | ✅ Safe to sell |
|---|---|
| Kits, gear, weapons, buffs | Cosmetics (kill effects, trails, pets, emotes) |
| ELO or XP boosts that unlock gameplay | Ranks with **social / cosmetic** perks |
| Talent points, paid classes, extra loadout power | **Skins** for classes and talents |
| Paid random crates (also a legal risk: minors, gambling law) | **Cosmetic-only** battle pass |
| Anything touching matchmaking, damage, or progression | Profile flair, badges, premium stats |

---

## The levers

### 1. Seasonal battle pass — *the main one*
A cosmetic-only pass, sold per season (~5 €). This is the modern model and it fits a ranked
ladder perfectly: **season of ranked → cosmetic pass → ELO reset → repeat.** It is recurring
revenue, it rewards *playing* rather than paying, and it is the single biggest lever.

**Engine needs:** seasons (ELO reset + archive), a per-player progression track, cosmetic rewards.

### 2. Ranks
One-off purchase (~10–30 €). Perks must stay **social or cosmetic**:
- coloured name, chat prefix, custom join message
- more cosmetic slots, `/nick`
- **queue priority** — this is access to the server, *not* an in-game advantage

**Engine needs:** a rank/permission concept, hooks in the sidebar, chat and queue.

### 3. Individual cosmetics
Sold à la carte or via the pass:
- kill effects, victory dances, arrow / projectile trails
- lobby pets and companions
- clan tags, clan banners

**Engine needs:** a cosmetic catalogue, owned-cosmetics per player, an "equipped" set applied
at match start and in the lobby.

### 4. Class & talent **skins**
This is the important one for us. Our class + talent system is, commercially, a
**cosmetic catalogue in disguise**:

> **The build is free. The look is sold.**

Every talent tree and class we add is also a set of skins we can sell — without ever touching
balance. Design each class with a cosmetic slot from day one.

### 5. Profile flair & premium stats
We sell **status and data**, never power:
- leaderboard badges, name borders, profile banners
- detailed match history, replays, personal statistics

Zero gameplay impact, so zero EULA risk — and it appeals to exactly the competitive players
who care most.

---

## Payments

**Tebex** is the standard for Minecraft stores (webstore, VAT, fraud, chargebacks).
Alternative: CraftingStore.

**France / EU reality check:** many buyers are **minors** → parental consent, refunds and
chargebacks are a real operational cost. Have a company structure, clear terms (CGV), GDPR
compliance and a written refund policy *before* taking the first euro.

---

## The honest economics

**Monetization is not the hard part. Acquisition is.**

On a live server, roughly **1–3 %** of active players pay anything. Revenue is therefore
almost a direct function of concurrent players. Most Minecraft servers earn **nothing** — not
because they monetized badly, but because nobody came.

So the real work is a **differentiator plus a content engine** (clips, creators, tournaments).
PvP is one of the few genres where the content makes itself: **duel highlights *are* the
marketing.**

---

## What this means for the engine

Design these in early — they are painful to retrofit, and impossible to retrofit *safely*
once players have paid for something:

- [ ] **Seasons**: ELO reset + archive of past seasons (needed by the battle pass)
- [ ] **Cosmetic system**: catalogue, ownership, equipped set, applied in lobby and in match
- [ ] **Cosmetic slot on every class / talent** from the day the class system lands
- [ ] **Ranks / permissions** hooked into chat, sidebar and queue priority
- [ ] **Store hooks** (Tebex → grant a cosmetic or rank, never an advantage)

The engine's job is to make the **safe** thing the easy thing: there should be no code path
that can grant gear, rating or talent points for money.

---

## Adjacent business: sell the engine itself

We are building a **modular, tested, documented PvP engine**. That is also a product, and it
carries **none of the cosmetic-only constraints**:

- licence / sell the plugin (BuiltByBit, Polymart — plugins there do reach five figures)
- setup and maintenance for other server owners
- white-label: engine + custom game modes

B2B: lower volume, higher ticket, no minors, no EULA cosmetic limits. It can fund the server
while the player base is being built.
