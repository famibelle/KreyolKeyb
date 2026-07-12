---
title: "Clavier Créole Guadeloupéen pour Android — Klavyé Kréyòl Karukera"
description: "Le clavier Android intelligent pour écrire en kréyòl guadeloupéen : suggestions bilingues créole/français, accents par appui long, 100 % hors ligne et gratuit."
lang: fr
---

# Klavyé Kréyòl Karukera — le clavier créole guadeloupéen pour Android

**Écrire en kréyòl sur son téléphone, sans galérer.** Klavyé Kréyòl Karukera
est un clavier Android **gratuit, open source et 100 % hors ligne** qui
propose des suggestions de mots en **créole guadeloupéen**, construites sur
les textes des grands défenseurs du kréyòl : Sylviane Telchid, Sonny Rupaire,
Max Rippon, Robert Fontes, Esnard Boisdur et bien d'autres.

<div align="center">
  <a href="https://play.google.com/store/apps/details?id=com.potomitan.kreyolkeyboard&referrer=utm_source%3Dlanding%26utm_campaign%3Dlaunch10k">
    <img src="Screenshots/GetItOnGooglePlay_Badge_Web_color_French.svg" alt="Télécharger Klavyé Kréyòl Karukera sur Google Play" width="60%">
  </a>
</div>

## Le clavier en action

<div align="center" style="display: flex; justify-content: center; gap: 10px; flex-wrap: wrap;">
   <img src="Screenshots/KlavyéAnAktion.gif" alt="Clavier créole guadeloupéen en action avec suggestions kréyòl" width="25%">
   <img src="Screenshots/Screenshot_1761763560.png" alt="Suggestions de mots en créole guadeloupéen" width="25%">
   <img src="Screenshots/Screenshot_1761763491.png" alt="Clavier kréyòl avec accents" width="25%">
</div>

## Pourquoi ce clavier ?

- 🤔 Ton téléphone **refuse tous les mots créoles** et « corrige » ton kréyòl en français ?
- 😤 Tu **doutes de l'orthographe** à chaque message ?
- ➡️ Klavyé Kréyòl Karukera est fait pour toi.

## Fonctionnalités

| | |
|---|---|
| 💡 **Suggestions intelligentes** | Dictionnaire de 1 800+ mots créoles et modèle de prédiction construit sur un corpus littéraire créole authentique |
| 🔤 **Accents faciles** | Appui long sur une lettre pour é, è, à, ò et tous les caractères du kréyòl |
| 🇫🇷 **Bilingue** | Le français prend le relais quand aucun mot créole ne correspond |
| 🎮 **Jeux de vocabulaire** | Mots Mêlés et Mots Mélangés pour apprendre en s'amusant |
| 🏆 **Progression culturelle** | 8 niveaux, de Pipirit à Benzo, au fil de tes mots tapés |
| 🔒 **Zéro collecte de données** | Fonctionnement 100 % local : rien ne quitte ton téléphone ([politique de confidentialité](privacy/privacy-policy.html)) |
| 🆓 **Gratuit et open source** | Code public sur [GitHub](https://github.com/famibelle/KreyolKeyb), licence MIT |

## Un projet de préservation linguistique

Ce n'est pas juste un clavier : chaque message écrit en kréyòl aide notre
langue à exister dans le numérique. Le dictionnaire et les suggestions
s'appuient sur les œuvres d'écrivains, de linguistes et d'artistes qui ont
donné au créole guadeloupéen ses lettres de noblesse.

## La jauge des 10 000 📲

<div id="dl-gauge" style="background:#fff;border:1px solid #ddd;border-radius:12px;padding:20px;margin:16px 0;">
  <div style="display:flex;justify-content:space-between;align-items:baseline;flex-wrap:wrap;gap:6px;margin-bottom:10px;">
    <span style="font-size:24px;font-weight:700;font-variant-numeric:tabular-nums;"><span id="g-current">…</span> <small style="font-size:14px;font-weight:400;color:#666;">téléchargements — prochain palier : <span id="g-next">…</span></small></span>
    <span style="font-weight:700;color:#C94A3B;">Objectif du jour : <span id="g-daily">60</span> 📲</span>
  </div>
  <div style="height:16px;border-radius:999px;background:#DDEEEE;overflow:hidden;">
    <div id="g-fill" style="height:100%;border-radius:999px;background:linear-gradient(90deg,#0E6E76,#C97F1E);min-width:8px;width:1%;transition:width .8s ease;"></div>
  </div>
  <div style="display:flex;justify-content:space-between;flex-wrap:wrap;gap:6px;font-size:12.5px;color:#666;margin-top:8px;">
    <span id="g-remaining">An nou ay ! Chaque téléchargement fait vivre le kréyòl 🏝️</span>
    <span>Objectif final : 10 000 · <span id="g-asof"></span></span>
  </div>
</div>

<script>
fetch('stats/downloads.json').then(function(r){ return r.json(); }).then(function(s){
  var fmt = function(n){ return n.toLocaleString('fr-FR'); };
  var base = Math.floor(s.current / 100) * 100;
  var next = Math.min(base + 100, s.goal);
  if (next <= s.current) { next = Math.min(s.current + 100, s.goal); base = next - 100; }
  var pct = Math.max(2, Math.min(100, ((s.current - base) / (next - base)) * 100));
  document.getElementById('g-current').textContent = fmt(s.current);
  document.getElementById('g-next').textContent = fmt(next);
  document.getElementById('g-daily').textContent = fmt(s.daily_target);
  document.getElementById('g-asof').textContent = 'MAJ ' + s.as_of;
  document.getElementById('g-remaining').textContent = 'Ka rété ' + fmt(next - s.current) + ' pou pwochen palyé-la ! 🏝️';
  document.getElementById('g-fill').style.width = pct + '%';
}).catch(function(){});
</script>

## Vin Anbasadè ! 📣

**Vous voulez aider le kréyòl à rayonner ?** Notre page ambassadeurs vous
donne tout : les contacts des médias locaux, les emails pré-remplis en un
clic, et quoi dire si vous appelez une radio.

<div align="center" style="margin: 16px 0;">
  <a href="ambassade.html" style="display:inline-block; background:#0E6E76; color:#fff; padding:12px 28px; border-radius:8px; text-decoration:none; font-weight:bold;">🏝️ Devenir ambassadeur du Klavyé Kréyòl</a>
</div>

**Pou laprès :** [dossier de presse / press kit](presskit.html)

---

🏝️ *Potomitan™ — Teknoloji pou tout moun.
« An kréyòl nou ka palé, an kréyòl nou ka rivé ! »*
