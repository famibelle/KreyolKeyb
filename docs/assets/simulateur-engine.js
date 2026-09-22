/*
 * Moteur de suggestion du simulateur Klavyé Kréyòl Karukera.
 * Port JS fidèle de SuggestionEngine.kt / LevenshteinDistance.kt /
 * AccentTolerantMatcher.kt / BilingualSuggestion.kt (android_keyboard/).
 * Toute divergence de comportement avec l'app Android est un bug de ce fichier.
 */
(function (global) {
  'use strict';

  const MAX_SUGGESTIONS = 5; // 3 kréyòl + 2 français, comme SuggestionEngine.MAX_SUGGESTIONS
  const MIN_WORD_LENGTH = 1;

  // ---- AccentTolerantMatcher ----

  const NORMALIZATION_MAP = {};
  for (const c of 'àáâäãåāăą') NORMALIZATION_MAP[c] = 'a';
  for (const c of 'èéêëēėęě') NORMALIZATION_MAP[c] = 'e';
  for (const c of 'ìíîïīįĩ') NORMALIZATION_MAP[c] = 'i';
  for (const c of 'òóôöõøōőœ') NORMALIZATION_MAP[c] = 'o';
  for (const c of 'ùúûüūůũűų') NORMALIZATION_MAP[c] = 'u';
  for (const c of 'ýÿŷ') NORMALIZATION_MAP[c] = 'y';
  NORMALIZATION_MAP['ç'] = 'c';
  NORMALIZATION_MAP['ñ'] = 'n';

  const AccentTolerantMatcher = {
    normalize(text) {
      if (!text) return text;
      let out = '';
      for (const ch of text) {
        const lower = ch.toLowerCase();
        if (lower === 'ß') out += 'ss';
        else out += NORMALIZATION_MAP[lower] || lower;
      }
      return out;
    },
    matches(input, target) {
      return this.normalize(input) === this.normalize(target);
    },
    startsWith(input, dictionaryWord) {
      return this.normalize(dictionaryWord).startsWith(this.normalize(input));
    },
    hasAccents(word) {
      return word !== this.normalize(word);
    }
  };

  // ---- LevenshteinDistance ----

  function levenshtein(s1, s2) {
    const len1 = s1.length;
    const len2 = s2.length;
    if (len1 === 0) return len2;
    if (len2 === 0) return len1;

    const dp = [];
    for (let i = 0; i <= len1; i++) dp.push(new Array(len2 + 1).fill(0));
    for (let i = 0; i <= len1; i++) dp[i][0] = i;
    for (let j = 0; j <= len2; j++) dp[0][j] = j;

    for (let i = 1; i <= len1; i++) {
      for (let j = 1; j <= len2; j++) {
        const cost = s1[i - 1].toLowerCase() === s2[j - 1].toLowerCase() ? 0 : 1;
        dp[i][j] = Math.min(
          dp[i - 1][j] + 1,
          dp[i][j - 1] + 1,
          dp[i - 1][j - 1] + cost
        );
      }
    }
    return dp[len1][len2];
  }

  // dictionary: [[word, freq], ...] → [[word, freq, distance], ...]
  function findClosestMatches(input, dictionary, maxDistance, maxResults, lengthTolerance) {
    if (!input) return [];
    const inputLength = input.length;
    const candidates = dictionary.filter(
      ([word]) => Math.abs(word.length - inputLength) <= lengthTolerance
    );
    return candidates
      .map(([word, freq]) => [word, freq, levenshtein(input, word)])
      .filter(([, , d]) => d <= maxDistance)
      .sort((a, b) => a[2] - b[2] || b[1] - a[1])
      .slice(0, maxResults);
  }

  function findClosestMatchesNormalized(input, dictionary, normalizer, maxDistance, maxResults) {
    if (!input) return [];
    const normalizedInput = normalizer(input);
    const inputLength = normalizedInput.length;
    const candidates = dictionary.filter(
      ([word]) => Math.abs(normalizer(word).length - inputLength) <= 2
    );
    return candidates
      .map(([word, freq]) => [word, freq, levenshtein(normalizedInput, normalizer(word))])
      .filter(([, , d]) => d <= maxDistance)
      .sort((a, b) => a[2] - b[2] || b[1] - a[1])
      .slice(0, maxResults);
  }

  // ---- casing / scoring (SuggestionEngine companion) ----

  function isLetter(ch) {
    return /\p{L}/u.test(ch);
  }
  function isUpper(ch) {
    return isLetter(ch) && ch === ch.toUpperCase() && ch !== ch.toLowerCase();
  }
  function isLower(ch) {
    return isLetter(ch) && ch === ch.toLowerCase() && ch !== ch.toUpperCase();
  }

  function applyCasingPattern(input, suggestion) {
    if (!input || !suggestion) return suggestion;

    const letters = [...input].filter(isLetter);
    if (letters.length >= 2 && letters.every(isUpper)) {
      return suggestion.toUpperCase();
    }

    if (input.length >= 1 && isUpper(input[0]) &&
        [...input.slice(1)].every((ch) => isLower(ch) || !isLetter(ch))) {
      return suggestion.charAt(0).toUpperCase() + suggestion.slice(1);
    }

    let result = '';
    for (let i = 0; i < suggestion.length; i++) {
      if (i < input.length) {
        const inputChar = input[i];
        const suggestionChar = suggestion[i];
        if (isUpper(inputChar)) result += suggestionChar.toUpperCase();
        else if (isLower(inputChar)) result += suggestionChar.toLowerCase();
        else result += suggestionChar;
      } else {
        result += suggestion[i];
      }
    }
    return result;
  }

  function calculateDictionaryScore(word, input, frequency, levenshteinDistance) {
    let score = frequency;
    const distance = levenshteinDistance || 0;

    if (distance > 0) {
      score += (3 - distance) * 100000;
    }
    if (AccentTolerantMatcher.startsWith(input, word)) {
      score += 50;
    }
    if (word.length <= 6) {
      score += 10;
    }
    if (word.length > 12) {
      score -= 10;
    }
    if (AccentTolerantMatcher.hasAccents(word)) {
      score += 5;
    }
    return score;
  }

  // ---- AccentRestoration ----
  /*
   * Port de AccentRestoration.kt. C'est la seule correction automatique du
   * clavier, et elle est bornée : le mot rendu a exactement les lettres du mot
   * tapé, aux accents près. Elle ne remplace jamais un mot par un autre, et
   * s'abstient dès que le corpus ne tranche pas. Les seuils sont ceux du Kotlin,
   * repris nom pour nom : toute divergence ici est un bug de ce fichier.
   */
  const AccentRestoration = {
    LONGUEUR_MINIMALE: 2,
    FREQUENCE_MINIMALE: 2,
    DOMINANCE_ENTRE_ACCENTUEES: 5,
    DOMINANCE_SUR_LA_FORME_NUE: 8,
    FREQUENCE_COQUILLE: 3,
    DOMINANCE_SUR_LA_COQUILLE: 2,
    FREQUENCE_POUR_SUPPLANTER: 4,
    LONGUEUR_FORME_NUE_MUETTE: 3,

    /**
     * @param tape mot tel que tapé, dans sa casse d'origine
     * @param groupe [[graphie, frequence], ...] ne différant de `tape` que par les accents
     * @param estMotFrancais vrai si `tape` figure au lexique français du clavier
     * @param nueAUnSensPropre vrai si la forme sans accent est elle-même glosée
     * @returns la graphie à substituer, ou null s'il faut laisser le mot tel quel
     */
    choisir(tape, groupe, estMotFrancais, nueAUnSensPropre) {
      if (tape.length < this.LONGUEUR_MINIMALE || estMotFrancais) return null;
      if (![...tape].every(isLetter)) return null;

      const minuscule = tape.toLowerCase();
      // Un accent tapé est un choix : on ne le réécrit pas, même s'il est faux.
      if (AccentTolerantMatcher.hasAccents(minuscule)) return null;

      let accentuees = groupe
        .filter(([mot, freq]) => AccentTolerantMatcher.hasAccents(mot) && freq >= this.FREQUENCE_MINIMALE)
        .sort((a, b) => b[1] - a[1]);
      const meilleure = accentuees[0];
      if (!meilleure) return null;

      // Une rivale vue moins de trois fois est une coquille du corpus, pas une
      // norme concurrente : elle ne bloque pas la correction.
      if (accentuees.length > 1 && meilleure[1] >= this.FREQUENCE_POUR_SUPPLANTER) {
        accentuees = [meilleure].concat(
          accentuees.slice(1).filter(
            (rivale) => !(rivale[1] < this.FREQUENCE_COQUILLE &&
              meilleure[1] >= this.DOMINANCE_SUR_LA_COQUILLE * rivale[1])
          )
        );
      }

      // Deux graphies accentuées à peu près à égalité : question de norme, pas
      // de correction. Le clavier n'a pas à la trancher.
      if (accentuees.length > 1 && meilleure[1] < this.DOMINANCE_ENTRE_ACCENTUEES * accentuees[1][1]) {
        return null;
      }

      const entreeNue = groupe.find(([mot]) => mot === minuscule);
      const frequenceNue = entreeNue ? entreeNue[1] : 0;
      if (frequenceNue > 0) {
        const nueMuette = minuscule.length >= this.LONGUEUR_FORME_NUE_MUETTE &&
          !nueAUnSensPropre &&
          meilleure[1] >= this.FREQUENCE_POUR_SUPPLANTER;
        if (nueMuette) {
          if (meilleure[1] <= frequenceNue) return null;
        } else if (meilleure[1] < this.DOMINANCE_SUR_LA_FORME_NUE * frequenceNue) {
          return null;
        }
      }

      return applyCasingPattern(tape, meilleure[0]);
    }
  };

  // ---- BilingualConfig defaults (BilingualSuggestion.kt) ----

  const DEFAULT_BILINGUAL_CONFIG = {
    frenchActivationThreshold: 3,
    maxKreyolSuggestions: 3,
    maxFrenchSuggestions: 2,
    kreyolPriorityBoost: 1.5,
    frenchPenalty: 0.8,
    enableFrenchSupport: true,
    kreyolOnlyMode: false
  };

  // ---- SuggestionEngine ----

  class SuggestionEngine {
    constructor() {
      this.dictionary = []; // [[word, freq], ...] trié par fréquence décroissante
      this.normalizedWords = [];
      this.ngramModel = {}; // { word: [{word, probability}, ...] }
      this.frenchWords = []; // [[word, freq], ...]
      this.frenchSet = new Set(); // appartenance exacte, pour restoreAccents
      // Graphies du dictionnaire regroupées par forme sans accent, construites
      // une fois au chargement : la restauration ne parcourt alors plus rien.
      this.accentGroups = new Map();
      this.glosees = new Set(); // clés de creole_translations.json (TranslationDictionary.entrees)
      this.wordHistory = [];
      this.bilingualConfig = { ...DEFAULT_BILINGUAL_CONFIG };
    }

    loadDictionary(rawArray) {
      const list = rawArray.map(([word, freq]) => [String(word).toLowerCase(), freq || 1]);
      list.sort((a, b) => b[1] - a[1]);
      this.dictionary = list;
      this.normalizedWords = list.map(([word]) => AccentTolerantMatcher.normalize(word));

      this.accentGroups = new Map();
      list.forEach((entree, i) => {
        const cle = this.normalizedWords[i];
        const groupe = this.accentGroups.get(cle);
        if (groupe) groupe.push(entree);
        else this.accentGroups.set(cle, [entree]);
      });
    }

    loadFrenchDictionary(raw) {
      const list = (raw.words || []).map(([word, freq]) => [String(word).toLowerCase(), freq || 1]);
      list.sort((a, b) => b[1] - a[1]);
      this.frenchWords = list;
      this.frenchSet = new Set(list.map(([word]) => word));
    }

    loadNgramModel(raw) {
      this.ngramModel = raw || {};
    }

    /**
     * Table de gloses (creole_translations.json). Seules les clés servent ici :
     * une forme sans accent qui y figure porte un sens propre (« bo » le baiser
     * en face de « bò » le côté) et n'est donc pas réécrite.
     */
    loadTranslations(raw) {
      const table = (raw && raw.translations) || {};
      this.glosees = new Set(Object.keys(table));
    }

    /**
     * La graphie accentuée à substituer à `word` tapé sans accent, ou null.
     * Port de SuggestionEngine.restoreAccents() : toute la règle est dans
     * AccentRestoration, ici on ne fait que lui fournir le groupe de graphies,
     * dire si le mot est français, et si la forme nue est glosée.
     */
    restoreAccents(word) {
      if (word.length < AccentRestoration.LONGUEUR_MINIMALE) return null;
      const groupe = this.accentGroups.get(AccentTolerantMatcher.normalize(word));
      if (!groupe) return null;
      const minuscule = word.toLowerCase();
      return AccentRestoration.choisir(
        word,
        groupe,
        this.frenchSet.has(minuscule),
        this.glosees.has(minuscule)
      );
    }

    addWordToHistory(word) {
      const clean = word.toLowerCase().trim();
      if (clean.length >= MIN_WORD_LENGTH) {
        this.wordHistory.push(clean);
        if (this.wordHistory.length > 5) this.wordHistory.shift();
      }
    }

    clearHistory() {
      this.wordHistory = [];
    }

    shouldActivateFrench(input) {
      const c = this.bilingualConfig;
      return c.enableFrenchSupport && !c.kreyolOnlyMode && input.length >= c.frenchActivationThreshold;
    }

    adjustScoreByLanguage(score, language) {
      const c = this.bilingualConfig;
      return language === 'KREYOL' ? score * c.kreyolPriorityBoost : score * c.frenchPenalty;
    }

    // → [[word, freq, distance], ...]
    getDictionarySuggestions(input) {
      if (input.length < MIN_WORD_LENGTH) return [];

      const normalizedInput = AccentTolerantMatcher.normalize(input);
      const matches = [];
      for (let i = 0; i < this.dictionary.length; i++) {
        if (this.normalizedWords[i].startsWith(normalizedInput)) {
          matches.push([this.dictionary[i][0], this.dictionary[i][1], 0]);
          if (matches.length >= MAX_SUGGESTIONS * 2) break;
        }
      }

      if (matches.length === 0 && input.length >= 3) {
        return this.getSpellCorrectionSuggestions(input);
      }
      return matches;
    }

    getSpellCorrectionSuggestions(input) {
      if (input.length < 3) return [];

      const normalizedMatches = findClosestMatchesNormalized(
        input,
        this.dictionary,
        (str) => AccentTolerantMatcher.normalize(str),
        2,
        MAX_SUGGESTIONS
      );
      if (normalizedMatches.length > 0) return normalizedMatches;

      return findClosestMatches(input, this.dictionary, 2, MAX_SUGGESTIONS, 2);
    }

    /**
     * Clé à interroger dans le modèle : le contexte à deux mots (« an ka ») s'il
     * y figure, sinon le dernier mot seul (« ka »). Port de
     * SuggestionEngine.resolveNgramContext(). Les deux familles de clés vivent
     * dans le même objet plat, sans collision possible : la tokenisation du
     * pipeline exclut les espaces.
     */
    resolveNgramContext(previousWord, lastWord) {
      if (previousWord) {
        const paire = previousWord + ' ' + lastWord;
        if (Object.prototype.hasOwnProperty.call(this.ngramModel, paire)) return paire;
      }
      return lastWord;
    }

    // → [word, ...] triés par probabilité décroissante
    getNgramSuggestions() {
      const lastWord = this.wordHistory[this.wordHistory.length - 1];
      if (!lastWord) return [];
      const previousWord = this.wordHistory[this.wordHistory.length - 2];
      const list = this.ngramModel[this.resolveNgramContext(previousWord, lastWord)];
      if (!list) return [];

      const seen = new Set();
      const suggestions = [];
      for (const entry of list) {
        const word = entry.word;
        const prob = typeof entry.probability === 'number' ? entry.probability : 0;
        if (word && !seen.has(word)) {
          seen.add(word);
          suggestions.push([word, prob]);
        }
      }
      suggestions.sort((a, b) => b[1] - a[1]);
      return suggestions.slice(0, MAX_SUGGESTIONS).map((s) => s[0]);
    }

    // → [{word, score, language}, ...] casse déjà appliquée
    getKreyolSuggestions(input) {
      const dictMatches = this.getDictionarySuggestions(input);
      const ngramMatches = this.wordHistory.length > 0 ? this.getNgramSuggestions() : [];

      const scores = new Map();
      for (const [word, freq, distance] of dictMatches) {
        scores.set(word, calculateDictionaryScore(word, input, freq, distance));
      }

      const lowerInput = input.toLowerCase();
      for (const word of ngramMatches) {
        if (word.toLowerCase().startsWith(lowerInput)) {
          scores.set(word, (scores.get(word) || 0) + 50);
        }
      }

      const result = [...scores.entries()].map(([word, score]) => ({
        word: applyCasingPattern(input, word),
        score: this.adjustScoreByLanguage(score, 'KREYOL'),
        language: 'KREYOL'
      }));
      result.sort((a, b) => b.score - a.score);
      return result.slice(0, this.bilingualConfig.maxKreyolSuggestions);
    }

    getFrenchSuggestions(input) {
      const prefix = input.toLowerCase();
      if (!this.frenchWords.length) return [];

      const matches = this.frenchWords
        .filter(([word]) => word.startsWith(prefix))
        .sort((a, b) => b[1] - a[1] || a[0].length - b[0].length)
        .slice(0, this.bilingualConfig.maxFrenchSuggestions);

      const result = matches.map(([word, freq]) => {
        const baseScore = calculateDictionaryScore(word, input, freq, 0);
        return {
          word: applyCasingPattern(input, word),
          score: this.adjustScoreByLanguage(baseScore, 'FRENCH'),
          language: 'FRENCH'
        };
      });
      result.sort((a, b) => b.score - a.score);
      return result;
    }

    // Positions 1-3 réservées kréyòl, 4-5 français optionnel (mergeSuggestionsKreyolFirst)
    mergeSuggestionsKreyolFirst(kreyolSuggs, frenchSuggs) {
      const result = [];
      const used = new Set();

      for (const s of kreyolSuggs.slice(0, 3)) {
        const key = s.word.toLowerCase();
        if (!used.has(key)) {
          result.push(s);
          used.add(key);
        }
      }
      for (const s of frenchSuggs.slice(0, 2)) {
        const key = s.word.toLowerCase();
        if (result.length < MAX_SUGGESTIONS && !used.has(key)) {
          result.push(s);
          used.add(key);
        }
      }
      for (const s of kreyolSuggs.slice(3)) {
        const key = s.word.toLowerCase();
        if (result.length < MAX_SUGGESTIONS && !used.has(key)) {
          result.push(s);
          used.add(key);
        }
      }
      return result;
    }

    // Suggestions bilingues (mode frappe) — équivalent generateBilingualSuggestions()
    generateBilingualSuggestions(input) {
      if (input.length < MIN_WORD_LENGTH) return [];
      const kreyol = this.getKreyolSuggestions(input);
      const french = this.shouldActivateFrench(input) ? this.getFrenchSuggestions(input) : [];
      return this.mergeSuggestionsKreyolFirst(kreyol, french);
    }

    // Prédictions contextuelles n-gram (mode après espace) — kréyòl uniquement
    generateContextualSuggestions() {
      if (this.wordHistory.length === 0 || Object.keys(this.ngramModel).length === 0) return [];
      return this.getNgramSuggestions();
    }
  }

  global.KreyolSimulatorEngine = {
    SuggestionEngine,
    AccentRestoration,
    AccentTolerantMatcher,
    levenshtein,
    applyCasingPattern,
    calculateDictionaryScore
  };
})(typeof window !== 'undefined' ? window : globalThis);

// Export CommonJS pour les tests Node (sans effet dans le navigateur)
if (typeof module !== 'undefined' && module.exports) {
  module.exports = globalThis.KreyolSimulatorEngine;
}
