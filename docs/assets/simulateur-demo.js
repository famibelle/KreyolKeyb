/*
 * Rejoue une phrase sur le simulateur et compte les frappes réellement
 * nécessaires. Le compteur n'est pas une estimation : la phrase est tapée
 * lettre à lettre dans le vrai moteur, et dès que le mot visé apparaît dans
 * les suggestions kréyòl affichées, la pastille est touchée, exactement ce
 * que ferait quelqu'un sur son téléphone.
 *
 * Adapté du simulateur de LuxKeyb. Fichier séparé de simulateur-ui.js à
 * dessein : celui-ci est partagé entre les deux projets, un fichier neuf ne
 * peut pas entrer en conflit lors d'une fusion.
 *
 * Deux différences avec le clavier luxembourgeois :
 * - il y a deux rangées de suggestions (kréyòl et français), et seule la
 *   première est consultée : on rejoue une phrase kréyòl ;
 * - le corpus écrit une espace avant « ? » et « ! » (« Nou kay bengné ! »),
 *   on la garde ; pour « , » et « . » elle est retirée.
 */
(function (global) {
  'use strict';

  const DELAI = { lettre: 105, apresMot: 190, avantPastille: 230, contexte: 140 };
  const LETTRE_RE = /[a-zA-ZàâäçéèêëîïôöòùûüÿñæœÀÂÄÇÉÈÊËÎÏÔÖÒÙÛÜŸÑÆŒ]/;
  const PONCTUATION_ESPACEE = new Set(['?', '!']);
  const PONCTUATION_FINALE = new Set([',', '.', '?', '!']);

  const dodo = (ms) => new Promise((r) => setTimeout(r, ms));
  const plier = (s) =>
    global.KreyolSimulatorEngine.AccentTolerantMatcher.normalize(String(s));

  // « Nou kay bengné ! » → ['Nou', 'kay', 'bengné', '!']
  function decouper(phrase) {
    const jetons = [];
    let courant = '';
    for (const ch of phrase) {
      if (LETTRE_RE.test(ch)) {
        courant += ch;
      } else {
        if (courant) { jetons.push(courant); courant = ''; }
        if (ch !== ' ') jetons.push(ch);
      }
    }
    if (courant) jetons.push(courant);
    return jetons;
  }

  class RejeuDemo {
    constructor(sim, els) {
      this.sim = sim;
      this.els = els;
      this.enCours = false;
      this.jeton = 0; // invalide un rejeu quand un autre démarre
    }

    pastillesAffichees() {
      return Array.from(this.els.rowKreyol.querySelectorAll('.chip'))
        .map((b) => b.textContent);
    }

    // Le mot visé est-il proposé ? On compare replié : la pastille peut
    // porter la casse canonique que l'utilisateur n'a pas tapée.
    pastillePour(mot) {
      const cible = plier(mot);
      return this.pastillesAffichees().find((w) => plier(w) === cible) || null;
    }

    majCompteur(frappes, texte, fini) {
      const caracteres = texte.trim().length;
      const gain = caracteres ? Math.round(100 - (100 * frappes) / caracteres) : 0;
      this.els.compteurFrappes.textContent = frappes;
      this.els.compteurCaracteres.textContent = caracteres;
      this.els.compteurGain.textContent = fini && gain > 0 ? '−' + gain + ' %' : '';
      this.els.compteur.classList.toggle('fini', Boolean(fini));
    }

    arreter() {
      this.jeton++;
      this.enCours = false;
      this.els.demoSection.classList.remove('joue');
      document.body.classList.remove('demo-joue');
    }

    async jouer(phrase) {
      this.arreter();
      const monJeton = ++this.jeton;
      const vivant = () => this.jeton === monJeton;

      this.enCours = true;
      this.els.demoSection.classList.add('joue');
      document.body.classList.add('demo-joue');
      this.els.compteur.hidden = false;
      this.sim.reset();

      let frappes = 0;
      this.majCompteur(0, '', false);

      for (const jeton of decouper(phrase)) {
        if (!vivant()) return;

        if (!LETTRE_RE.test(jeton)) {
          // Ponctuation : la pastille du mot précédent a déjà posé une
          // espace, qu'on garde avant « ? » et « ! » et qu'on retire sinon.
          if (!PONCTUATION_ESPACEE.has(jeton)) {
            this.sim.screenText = this.sim.screenText.replace(/ $/, '');
          }
          this.sim.insertPhysicalChar(jeton);
          frappes++;
          this.majCompteur(frappes, this.sim.screenText, false);
          await dodo(DELAI.lettre);
          if (PONCTUATION_FINALE.has(jeton)) {
            this.sim.processKey(' ');
            await dodo(DELAI.contexte);
          }
          continue;
        }

        // 1. le mot est-il déjà prédit, avant la moindre lettre ?
        await dodo(DELAI.contexte);
        let pastille = this.pastillePour(jeton);
        if (pastille) {
          await dodo(DELAI.avantPastille);
          if (!vivant()) return;
          this.sim.selectSuggestion(pastille);
          frappes++;
          this.majCompteur(frappes, this.sim.screenText, false);
          await dodo(DELAI.apresMot);
          continue;
        }

        // 2. sinon on tape, en surveillant les suggestions à chaque lettre
        let pose = false;
        for (let i = 0; i < jeton.length; i++) {
          if (!vivant()) return;
          this.sim.insertPhysicalChar(jeton[i]);
          frappes++;
          this.majCompteur(frappes, this.sim.screenText, false);
          await dodo(DELAI.lettre);

          if (i < jeton.length - 1) {
            pastille = this.pastillePour(jeton);
            if (pastille) {
              await dodo(DELAI.avantPastille);
              if (!vivant()) return;
              this.sim.selectSuggestion(pastille);
              frappes++;
              this.majCompteur(frappes, this.sim.screenText, false);
              pose = true;
              break;
            }
          }
        }
        if (!pose) {
          this.sim.processKey(' '); // valider le mot compte aussi une frappe
          frappes++;
          this.majCompteur(frappes, this.sim.screenText, false);
        }
        await dodo(DELAI.apresMot);
      }

      if (!vivant()) return;
      this.enCours = false;
      this.els.demoSection.classList.remove('joue');
      document.body.classList.remove('demo-joue');
      this.majCompteur(frappes, this.sim.screenText, true);
    }
  }

  global.KreyolSimulatorDemo = RejeuDemo;
})(typeof window !== 'undefined' ? window : globalThis);
