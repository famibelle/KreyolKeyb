/*
 * Interface du simulateur de clavier Klavyé Kréyòl Karukera.
 * Port du comportement de InputProcessor.kt / KeyboardLayoutManager.kt /
 * AccentHandler.kt / KreyolInputMethodServiceRefactored.kt (android_keyboard/),
 * réutilisant le moteur de suggestions dans simulateur-engine.js.
 */
(function () {
  'use strict';

  const ALPHA_ROWS = [
    // 10 touches depuis la v10.11.3 : « ò » a quitté cette rangée, la plus frappée
    // du clavier et pourtant la plus étroite quand elle en portait 11. Il reste en
    // appui long sur « o », où l'indice de coin l'affiche. Cf. KeyboardLayoutManager.
    ['a', 'z', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'],
    ['q', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'm'],
    // v10.11.4 : l'apostrophe rejoint cette rangée, financée par ⇧ et ⌫ dont le
    // poids passe de 1,5 à 1,25 comme dans KeyboardLayoutManager.
    ['⇧', 'w', 'x', 'c', 'v', 'b', 'n', "'", '⌫'],
    ['123', ',', 'é', '-', ' ', 'è', '.', 'EMOJI', '⏎']
  ];

  const NUMERIC_ROWS = [
    ['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'],
    ['-', '/', ':', ';', '(', ')', '€', '&', '@', '"'],
    // v10.12.15 : « # » ajouté. C'est la seule page de symboles du clavier, donc
    // son absence signifiait qu'aucun hashtag ne pouvait être écrit.
    ['=', '.', ',', '?', '!', "'", '+', '*', '#', '⌫'],
    ['ABC', 'EMOJI', ' ', '⏎']
  ];

  // Rangée de contrôle sous le panneau emoji (createEmojiLayout()).
  const EMOJI_CONTROL_ROW = ['ABC', '⌫', ' ', '⏎'];

  // Géométrie et tailles de police reprises de KeyboardLayoutManager. Le
  // téléphone du simulateur est dessiné à la largeur d'un écran de 360 dp, donc
  // un dp y vaut un pixel et ces constantes se lisent telles quelles.
  const KEY_HEIGHT_PX = 48;
  const KEY_TEXT_HEIGHT_RATIO = 0.62;
  const WIDE_LABEL_TEXT_RATIO = 0.28;
  const SPACE_LABEL_TEXT_RATIO = 0.22;
  const LABEL_WIDTH_RATIO = 0.90;
  const EMOJI_WIDTH_RATIO = 0.77;

  // Les trois touches à icône de l'application (ic_shift_off/on/caps.xml,
  // ic_backspace.xml, ic_keyboard_return.xml). Les tracés sont recopiés tels
  // quels : le pathData d'un vector Android est un « d » SVG. Le padding, lui
  // aussi repris de createKeyButton(), s'exprime en part de la hauteur de touche
  // et l'icône se dessine dedans comme un FIT_CENTER.
  const ICONS = {
    backspace: {
      viewBox: '0 0 24 24',
      d: 'M22,3L7,3c-0.69,0 -1.23,0.35 -1.59,0.88L0,12l5.41,8.11c0.36,0.53 0.9,0.89 1.59,0.89h15c1.1,0 2,-0.9 2,-2L24,5c0,-1.1 -0.9,-2 -2,-2zM19,15.59L17.59,17 14,13.41 10.41,17 9,15.59 12.59,12 9,8.41 10.41,7 14,10.59 17.59,7 19,8.41 15.41,12 19,15.59z',
      padRatio: 10 / 48
    },
    // Cadre recadré sur l'encre (19 × 12) et tracé replacé dedans, exactement
    // comme le fait le <group> du vector : sans cela la flèche ne remplirait que
    // la moitié de sa touche.
    enter: {
      viewBox: '0 0 19 12',
      d: 'M19,7v4H5.83l3.58,-3.59L8,6l-6,6 6,6 1.41,-1.41L5.83,13H21V7z',
      transform: 'translate(-2,-6)',
      padRatio: 4 / 48
    },
    shiftOff: {
      viewBox: '0 0 24 24',
      d: 'M12,3L3,12h5v6h8v-6h5L12,3zM12,5.83L16.17,10H14v6h-4v-6H7.83L12,5.83z',
      fillRule: 'evenodd',
      padRatio: 6 / 48
    },
    shiftOn: {
      viewBox: '0 0 24 24',
      d: 'M12,3L3,12h5v6h8v-6h5L12,3z',
      padRatio: 6 / 48
    },
    shiftCaps: {
      viewBox: '0 0 24 24',
      d: 'M12,2L3,11h5v5h8v-5h5L12,2zM8,18h8v2H8z',
      padRatio: 6 / 48
    }
  };

  // AccentHandler.kt accentMap (v10.14.x)
  const ACCENT_MAP = {
    a: ['à', 'â'],
    e: ['é', 'è', 'ê'],
    i: ['î', 'ï'],
    o: ['ò', 'ô', 'ó', 'œ'],
    u: ['ù', 'û'],
    c: ['ç', 'ch'],
    d: ['dj'],
    t: ['tj'],
    // L'apostrophe reste sous la virgule même depuis qu'elle a retrouvé une touche
    // dédiée (v10.11.4), comme é et è restent sous « e ».
    ',': [';', ':', "'"],
    '.': ['!', '?', '…']
    // Pas d'entrée pour « ' » : les guillemets qui vivaient sous son appui long ont
    // disparu avec elle en v9.1.0 (aucun usage relevé dans les dictionnaires) et
    // n'ont pas été rétablis côté application. Cf. AccentHandler.accentMap.
  };
  // AccentHandler.cornerHintOverrides : « e » affiche è puis é, alors que le popup
  // liste é avant è (fréquence décroissante).
  const CORNER_HINTS = { e: ['è', 'é'], o: ['ò', 'ó'] };
  const DEDICATED_ACCENTED_KEYS = new Set(['à', 'è', 'ò', 'é', 'ù', 'ì', 'ç']);
  const LETTER_RE = /^[a-zA-Zàáâãäåèéêëìíîïòóôõöøùúûüýÿñçĉĝĥĵŝŭ]$/;

  // Ponctuations qui valident le mot comme le ferait un espace, donc qui
  // rétablissent aussi les accents. Ni « - » ni « ' » n'y figurent : ils
  // prolongent un mot (« ba-w ») au lieu de le clore (InputProcessor.kt).
  const PONCTUATION_QUI_VALIDE = new Set([',', '.', ';', ':', '?', '!']);

  const DELAI_DOUBLE_ESPACE_MS = 700;

  /*
   * Le second espace d'un double espace devient « . » seulement s'il suit le
   * premier de près ET si ce premier suivait une lettre ou un chiffre. Le
   * chronomètre seul transformerait « bonjou, » en « ., ».
   */
  function doubleEspaceEnPoint(avantLeCurseur, ecouleMs) {
    return ecouleMs >= 0 && ecouleMs <= DELAI_DOUBLE_ESPACE_MS &&
      avantLeCurseur.length >= 2 &&
      avantLeCurseur[avantLeCurseur.length - 1] === ' ' &&
      /[\p{L}\p{N}]/u.test(avantLeCurseur[avantLeCurseur.length - 2]);
  }

  class KeyboardSimulator {
    constructor(engine, els) {
      this.engine = engine;
      this.els = els;

      this.screenText = '';
      this.currentWord = '';
      // États à un coup de la restauration d'accents, lus puis remis à zéro en
      // tête de processKey : chacun ne concerne que la touche qui suit l'action
      // qui l'a posé (InputProcessor.kt).
      this.derniereRestauration = null;
      this.motRefuse = null;
      this.instantDernierEspace = 0;
      this.isCapitalMode = false;
      this.isCapsLock = false;
      this.isNumericMode = false;
      this.isEmojiMode = false;
      // Le jeu d'emojis (~1900) n'est chargé qu'à la première ouverture du
      // panneau : la page n'a pas à payer 49 ko pour un mode qu'on n'ouvre pas.
      this.emojiData = null;
      this.emojiLoading = false;
      this.emojiCategory = 0;

      this.currentPopup = null;
      this._popupOutsideHandler = null;
      this._contextualTimer = null;

      this.els.caret = document.createElement('span');
      this.els.caret.className = 'phone-caret';

      this.renderKeyboard();
      this.renderScreen();
      this.updateSuggestions([], false);

      this.els.resetBtn?.addEventListener('click', () => this.reset());
      this.bindPhysicalKeyboard();

      // La taille des libellés dépend de la largeur réelle des touches, qui
      // change avec celle de la fenêtre : elle se recalcule à chaque
      // redimensionnement du clavier, et pas seulement au rendu.
      if (window.ResizeObserver) {
        new ResizeObserver(() => this.sizeKeyLabels()).observe(this.els.keyboard);
      }
    }

    // ---- clavier physique (confort desktop, en plus du clavier tactile) ----

    bindPhysicalKeyboard() {
      document.addEventListener('keydown', (e) => {
        if (e.ctrlKey || e.metaKey || e.altKey) return; // laisser les raccourcis navigateur

        const target = e.target;
        if (target && (target.tagName === 'A' || target.tagName === 'BUTTON' || target.isContentEditable)) {
          return; // laisser Tab+Entrée/Espace activer nativement liens, boutons, touches virtuelles
        }

        if (e.key === 'Backspace') {
          e.preventDefault(); // sinon navigation arrière du navigateur hors champ éditable
          this.processKey('⌫');
          return;
        }
        if (e.key === 'Enter') {
          e.preventDefault();
          this.processKey('⏎');
          return;
        }
        if (e.key === ' ') {
          e.preventDefault(); // sinon défilement de la page
          this.processKey(' ');
          return;
        }
        if (e.key === 'Escape') {
          this.dismissAccentPopup();
          return;
        }
        if (e.key.length === 1) {
          e.preventDefault();
          this.insertPhysicalChar(e.key);
        }
      });
    }

    // Comme handleCharacter(), mais sans reforcer la casse : le clavier
    // physique (Maj/Verr.Maj/touches mortes gérées par l'OS) fournit déjà
    // le bon caractère dans e.key.
    insertPhysicalChar(character) {
      this.instantDernierEspace = 0;
      if (LETTER_RE.test(character)) {
        this.derniereRestauration = null;
        this.currentWord += character;
        this.onWordChanged();
      } else {
        const restauration = PONCTUATION_QUI_VALIDE.has(character)
          ? this.restaurerLesAccents(character)
          : null;
        this.finalizeCurrentWord();
        this.derniereRestauration = restauration;
      }
      this.screenText += character;
      this.renderScreen();
      this.handleAutoCapitalization();
      this.renderKeyboard();
    }

    // ---- helpers de touche ----

    hasAccents(key) {
      return Object.prototype.hasOwnProperty.call(ACCENT_MAP, key.toLowerCase());
    }

    cornerHints(key) {
      const k = key.toLowerCase();
      if (!this.hasAccents(k)) return [];
      return CORNER_HINTS[k] || ACCENT_MAP[k];
    }

    displayText(key) {
      if (key === ' ') return 'Potomitan™';
      if (key === '⇧' || key === '⌫' || key === '⏎') return key;
      if (key === '123') return this.isNumericMode ? 'ABC' : '123';
      if (key === 'ABC') return 'ABC';
      if (key === 'EMOJI') return '😀';
      const upper = this.isCapitalMode || this.isCapsLock;
      if (DEDICATED_ACCENTED_KEYS.has(key)) return upper ? key.toUpperCase() : key;
      return upper ? key.toUpperCase() : key.toLowerCase();
    }

    keyClass(key) {
      if (key === '⇧') {
        if (this.isCapsLock) return 'kb-shift kb-shift-caps';
        if (this.isCapitalMode) return 'kb-shift kb-shift-on';
        return 'kb-shift';
      }
      if (key === '⌫') return 'kb-backspace';
      if (key === '⏎') return 'kb-enter';
      if (key === '123' || key === 'ABC' || key === 'EMOJI') return 'kb-mode';
      if (key === ' ') return 'kb-space-key';
      if ([',', '.', "'", '-'].includes(key)) return 'kb-punct';
      // Touches créoles dédiées, sur la même nuance que le shift au repos.
      if (DEDICATED_ACCENTED_KEYS.has(key)) return 'kb-accent';
      return 'kb-normal';
    }

    keyWeight(key) {
      if (key === ' ') return 4;
      // v10.11.4 : 1,5 → 1,25, cf. KeyboardLayoutManager.getKeyWeight()
      if (key === '⇧' || key === '⌫') return 1.25;
      return 1;
    }

    // ---- rendu clavier ----

    renderKeyboard() {
      this.els.keyboard.innerHTML = '';
      if (this.isEmojiMode) {
        this.els.keyboard.appendChild(this.createEmojiPanel());
        this.els.keyboard.appendChild(this.createRow(EMOJI_CONTROL_ROW));
      } else {
        const rows = this.isNumericMode ? NUMERIC_ROWS : ALPHA_ROWS;
        rows.forEach((rowKeys) => this.els.keyboard.appendChild(this.createRow(rowKeys)));
      }
      this.sizeKeyLabels();
    }

    createRow(rowKeys) {
      const rowEl = document.createElement('div');
      rowEl.className = 'kb-row';
      rowKeys.forEach((key) => rowEl.appendChild(this.createKey(key)));
      return rowEl;
    }

    /**
     * Taille de police d'un libellé : min(hauteur × ratio, largeur × ratio),
     * exactement createKeyButton(). La largeur d'une touche n'étant connue
     * qu'une fois la rangée disposée, elle se lit sur la touche rendue plutôt
     * que de refaire le partage des poids.
     */
    sizeKeyLabels() {
      this.els.keyboard.querySelectorAll('.kb-key').forEach((btn) => {
        const key = btn.dataset.key;
        if (key === undefined || ICONS[btn.dataset.icon]) return;
        const largeur = btn.getBoundingClientRect().width;
        if (!largeur) return;
        const ratioHauteur =
          key === ' ' ? SPACE_LABEL_TEXT_RATIO
          : (key === '123' || key === 'ABC') ? WIDE_LABEL_TEXT_RATIO
          : KEY_TEXT_HEIGHT_RATIO;
        const ratioLargeur = key === 'EMOJI' ? EMOJI_WIDTH_RATIO : LABEL_WIDTH_RATIO;
        btn.style.fontSize =
          Math.min(KEY_HEIGHT_PX * ratioHauteur, largeur * ratioLargeur).toFixed(1) + 'px';
      });
    }

    /** Icône d'une touche, ou null si elle porte un libellé texte. */
    iconFor(key) {
      if (key === '⌫') return 'backspace';
      if (key === '⏎') return 'enter';
      if (key !== '⇧') return null;
      if (this.isCapsLock) return 'shiftCaps';
      if (this.isCapitalMode) return 'shiftOn';
      return 'shiftOff';
    }

    createIcon(nom) {
      const icone = ICONS[nom];
      const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      svg.setAttribute('viewBox', icone.viewBox);
      svg.setAttribute('aria-hidden', 'true');
      svg.style.padding = (KEY_HEIGHT_PX * icone.padRatio).toFixed(1) + 'px';
      svg.style.boxSizing = 'border-box';
      const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      path.setAttribute('d', icone.d);
      if (icone.fillRule) path.setAttribute('fill-rule', icone.fillRule);
      if (icone.transform) path.setAttribute('transform', icone.transform);
      svg.appendChild(path);
      return svg;
    }

    createKey(key) {
      const wrap = document.createElement('div');
      wrap.className = 'kb-key-wrap';
      wrap.style.flexGrow = String(this.keyWeight(key));

      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'kb-key ' + this.keyClass(key);
      btn.dataset.key = key;

      const icone = this.iconFor(key);
      if (icone) {
        btn.dataset.icon = icone;
        btn.appendChild(this.createIcon(icone));
      } else {
        btn.textContent = this.displayText(key);
      }
      btn.setAttribute(
        'aria-label',
        key === ' ' ? 'Espace'
          : key === '⌫' ? 'Supprimer'
          : key === '⏎' ? 'Entrée'
          : key === '⇧' ? 'Majuscule'
          : key === 'EMOJI' ? 'Emojis'
          : key
      );

      let holdTimer = null;
      let longPressed = false;

      const startHold = (ev) => {
        ev.preventDefault();
        longPressed = false;
        btn.classList.add('kb-key-active');
        if (this.hasAccents(key)) {
          holdTimer = setTimeout(() => {
            longPressed = true;
            this.showAccentPopup(key, btn);
          }, 500);
        }
      };
      const endHold = (ev) => {
        ev.preventDefault();
        btn.classList.remove('kb-key-active');
        if (holdTimer) {
          clearTimeout(holdTimer);
          holdTimer = null;
        }
        if (!longPressed) {
          this.processKey(key);
        }
        longPressed = false;
      };
      const cancelHold = () => {
        btn.classList.remove('kb-key-active');
        if (holdTimer) {
          clearTimeout(holdTimer);
          holdTimer = null;
        }
        longPressed = false;
      };

      btn.addEventListener('pointerdown', startHold);
      btn.addEventListener('pointerup', endHold);
      btn.addEventListener('pointerleave', cancelHold);
      btn.addEventListener('pointercancel', cancelHold);
      btn.addEventListener('contextmenu', (e) => e.preventDefault());

      wrap.appendChild(btn);

      // Aperçu des deux premières options d'appui long, en haut et en bas du
      // côté droit de la touche — wrapWithLongPressHints() n'en connaît pas
      // d'autre côté.
      const hints = this.cornerHints(key);
      if (hints.length) {
        const punct = this.keyClass(key) === 'kb-punct';
        wrap.appendChild(this.createHint(hints[0], 'top', punct));
        if (hints[1]) wrap.appendChild(this.createHint(hints[1], 'bottom', punct));
      }

      // 🌐 : dans l'application, un appui long sur l'espace ouvre le sélecteur de
      // claviers du système. Le geste n'a pas d'équivalent dans un navigateur,
      // l'indice reste donc purement visuel ici, avec une infobulle qui le dit.
      if (key === ' ') {
        const globe = document.createElement('span');
        globe.className = 'kb-space-hint';
        globe.textContent = '🌐';
        globe.title = "Dans l'application, un appui long ici ouvre le sélecteur de claviers.";
        wrap.appendChild(globe);
      }

      return wrap;
    }

    createHint(text, vPos, punct) {
      const span = document.createElement('span');
      span.className = `kb-hint kb-hint-${vPos} kb-hint-end` + (punct ? ' kb-punct-hint' : '');
      span.textContent = text;
      return span;
    }

    // ---- panneau emoji (EmojiPickerView.kt) ----

    createEmojiPanel() {
      const panel = document.createElement('div');
      panel.className = 'kb-emoji-panel';

      if (!this.emojiData) {
        this.loadEmojiData();
        const message = document.createElement('div');
        message.className = 'kb-emoji-message';
        message.textContent = this.emojiLoading ? 'Chargement des emojis…' : 'Emojis indisponibles.';
        panel.appendChild(message);
        return panel;
      }

      const tabs = document.createElement('div');
      tabs.className = 'kb-emoji-tabs';
      this.emojiData.categories.forEach((categorie, index) => {
        const tab = document.createElement('button');
        tab.type = 'button';
        tab.className = 'kb-emoji-tab' + (index === this.emojiCategory ? ' is-active' : '');
        tab.textContent = categorie.icon;
        tab.title = categorie.name;
        tab.addEventListener('click', () => {
          this.emojiCategory = index;
          this.renderKeyboard();
        });
        tabs.appendChild(tab);
      });
      panel.appendChild(tabs);

      const grid = document.createElement('div');
      grid.className = 'kb-emoji-grid';
      this.emojiData.categories[this.emojiCategory].emojis.forEach((emoji) => {
        const cell = document.createElement('button');
        cell.type = 'button';
        cell.className = 'kb-emoji-cell';
        cell.textContent = emoji;
        cell.addEventListener('click', () => this.processKey(emoji));
        grid.appendChild(cell);
      });
      panel.appendChild(grid);

      return panel;
    }

    loadEmojiData() {
      if (this.emojiLoading || this.emojiData) return;
      this.emojiLoading = true;
      fetch('assets/emoji_data.json', { cache: 'force-cache' })
        .then((res) => {
          if (!res.ok) throw new Error('HTTP ' + res.status);
          return res.json();
        })
        .then((data) => {
          this.emojiData = data;
          this.emojiLoading = false;
          if (this.isEmojiMode) this.renderKeyboard();
        })
        .catch((err) => {
          this.emojiLoading = false;
          console.error(err);
          if (this.isEmojiMode) this.renderKeyboard();
        });
    }


    // ---- popup d'accents ----

    showAccentPopup(baseKey, anchorEl) {
      this.dismissAccentPopup();
      const accents = ACCENT_MAP[baseKey.toLowerCase()];
      if (!accents) return;

      const popup = document.createElement('div');
      popup.className = 'accent-popup';

      const makeBtn = (ch, isBase) => {
        const b = document.createElement('button');
        b.type = 'button';
        b.className = 'accent-btn' + (isBase ? ' accent-base' : '');
        const upper = this.isCapitalMode || this.isCapsLock;
        b.textContent = upper ? ch.toUpperCase() : ch;
        b.addEventListener('pointerdown', (e) => e.preventDefault());
        b.addEventListener('click', () => this.selectAccent(ch));
        return b;
      };

      popup.appendChild(makeBtn(baseKey, true));
      accents.forEach((a) => popup.appendChild(makeBtn(a, false)));

      document.body.appendChild(popup);
      const rect = anchorEl.getBoundingClientRect();
      const pRect = popup.getBoundingClientRect();
      let left = rect.left + rect.width / 2 - pRect.width / 2;
      left = Math.max(8, Math.min(left, window.innerWidth - pRect.width - 8));
      const top = Math.max(8, rect.top - pRect.height - 10);
      popup.style.left = left + 'px';
      popup.style.top = top + 'px';

      this.currentPopup = popup;
      this._popupOutsideHandler = (e) => {
        if (!popup.contains(e.target)) this.dismissAccentPopup();
      };
      setTimeout(() => document.addEventListener('pointerdown', this._popupOutsideHandler), 0);
    }

    dismissAccentPopup() {
      if (this.currentPopup) {
        this.currentPopup.remove();
        this.currentPopup = null;
      }
      if (this._popupOutsideHandler) {
        document.removeEventListener('pointerdown', this._popupOutsideHandler);
        this._popupOutsideHandler = null;
      }
    }

    // Sélection d'un accent (ou de la touche de base depuis le popup) : ajoutée
    // directement, sans régénérer les suggestions — comme onAccentSelected()
    // côté Android, qui met à jour le mot courant "silencieusement".
    selectAccent(accent) {
      const upper = this.isCapitalMode || this.isCapsLock;
      const finalAccent = upper ? accent.toUpperCase() : accent;
      this.screenText += finalAccent;
      this.currentWord += finalAccent;
      this.renderScreen();
      this.dismissAccentPopup();
    }

    // ---- logique de saisie (InputProcessor.kt) ----

    processKey(key) {
      // Mêmes états à un coup que InputProcessor.processKeyPress : lus ici pour
      // toutes les touches, remis à zéro sauf pour celle qui les exploite.
      const restauration = this.derniereRestauration;
      const instantEspace = this.instantDernierEspace;
      if (key !== '⌫') this.derniereRestauration = null;
      if (key !== ' ') this.instantDernierEspace = 0;

      switch (key) {
        case '⌫':
          this.handleBackspace(restauration);
          break;
        case '⏎':
          this.handleEnter();
          break;
        case '⇧':
          this.handleShift();
          break;
        case '123':
        case 'ABC':
          this.handleModeSwitch();
          break;
        case 'EMOJI':
          this.handleEmojiSwitch();
          break;
        case ' ':
          this.handleSpace(instantEspace);
          break;
        default:
          this.handleCharacter(key);
      }
      this.renderKeyboard();
    }

    handleCharacter(key) {
      const character = this.shouldCapitalize() ? key.toUpperCase() : key.toLowerCase();
      if (LETTER_RE.test(character)) {
        this.currentWord += character;
        this.onWordChanged();
      } else {
        // La ponctuation valide le mot comme le ferait un espace : les accents
        // s'y rétablissent aussi, avant que le signe ne soit posé.
        const restauration = PONCTUATION_QUI_VALIDE.has(character)
          ? this.restaurerLesAccents(character)
          : null;
        this.finalizeCurrentWord();
        this.derniereRestauration = restauration;
      }
      this.screenText += character;
      this.renderScreen();
      this.handleAutoCapitalization();
    }

    handleBackspace(restauration) {
      // Un retour arrière juste après une correction la défait, plutôt que
      // d'effacer une lettre que l'utilisateur n'a pas tapée.
      if (restauration && this.annulerRestauration(restauration)) {
        this.renderScreen();
        return;
      }
      if (!this.screenText.length) return;
      this.screenText = this.screenText.slice(0, -1);
      if (this.currentWord.length) {
        this.currentWord = this.currentWord.slice(0, -1);
        this.onWordChanged();
      }
      this.renderScreen();
    }

    handleEnter() {
      const restauration = this.restaurerLesAccents('\n');
      this.finalizeCurrentWord();
      this.screenText += '\n';
      this.derniereRestauration = restauration;
      this.renderScreen();
    }

    handleShift() {
      if (!this.isCapitalMode && !this.isCapsLock) {
        this.isCapitalMode = true;
        this.isCapsLock = false;
      } else if (this.isCapitalMode && !this.isCapsLock) {
        this.isCapitalMode = true;
        this.isCapsLock = true;
      } else {
        this.isCapitalMode = false;
        this.isCapsLock = false;
      }
    }

    /**
     * Bascule 123/ABC. Depuis le panneau emoji, « ABC » remonte ce même chemin :
     * on revient à l'alphabétique plutôt que de basculer le mode numérique, ce
     * qui ouvrirait le 123 au lieu des lettres (InputProcessor.handleModeSwitch).
     */
    handleModeSwitch() {
      if (this.isEmojiMode) {
        this.isEmojiMode = false;
        this.isNumericMode = false;
      } else {
        this.isNumericMode = !this.isNumericMode;
      }
    }

    handleEmojiSwitch() {
      this.isEmojiMode = true;
      this.isNumericMode = false;
    }

    handleSpace(instantPrecedent) {
      const maintenant = Date.now();

      // Deux espaces de suite après un mot : le second devient un point.
      if (instantPrecedent > 0 &&
          doubleEspaceEnPoint(this.screenText.slice(-2), maintenant - instantPrecedent)) {
        this.screenText = this.screenText.slice(0, -1) + '. ';
        this.instantDernierEspace = 0;
        this.renderScreen();
        this.handleAutoCapitalization();
        return;
      }

      const restauration = this.restaurerLesAccents(' ');
      this.finalizeCurrentWord();
      this.screenText += ' ';
      this.derniereRestauration = restauration;
      this.instantDernierEspace = maintenant;
      this.renderScreen();
      this.handleAutoCapitalization();
    }

    /**
     * Rétablit les accents du mot en cours et le réécrit à l'écran, ou rend null
     * s'il faut le laisser tel quel. Toute la règle est dans AccentRestoration ;
     * ici on ne gère que le texte et l'état (InputProcessor.restaurerLesAccents).
     *
     * @param suite le caractère qui vient valider le mot, conservé pour pouvoir
     *        défaire la correction au retour arrière suivant
     */
    restaurerLesAccents(suite) {
      const mot = this.currentWord;
      const refuse = this.motRefuse;
      this.motRefuse = null;
      if (mot.length < 2) return null;
      // Une correction déjà refusée une fois ne revient pas sur le même mot.
      if (refuse !== null && refuse === mot.toLowerCase()) return null;

      const restitue = this.engine.restoreAccents(mot);
      if (!restitue || restitue === mot) return null;
      if (!this.screenText.endsWith(mot)) return null;

      this.screenText = this.screenText.slice(0, -mot.length) + restitue;
      this.currentWord = restitue;
      return { tape: mot, restitue: restitue, suite: suite };
    }

    /**
     * Défait une correction : le mot corrigé et ce qui l'a validé sont retirés,
     * le mot tapé revient et redevient le mot en cours. Rend false si le texte a
     * changé depuis, auquel cas le retour arrière se fait comme d'habitude.
     */
    annulerRestauration(restauration) {
      const attendu = restauration.restitue + restauration.suite;
      if (!this.screenText.endsWith(attendu)) return false;

      this.screenText = this.screenText.slice(0, -attendu.length) + restauration.tape;
      this.currentWord = restauration.tape;
      this.motRefuse = restauration.tape.toLowerCase();
      this.onWordChanged();
      return true;
    }

    onWordChanged() {
      if (this.currentWord) {
        const suggestions = this.engine.generateBilingualSuggestions(this.currentWord);
        this.updateSuggestions(suggestions, true);
      } else {
        this.updateSuggestions([], false);
      }
    }

    finalizeCurrentWord() {
      if (!this.currentWord) {
        this.updateSuggestions([], false);
        return;
      }
      this.engine.addWordToHistory(this.currentWord);
      this.currentWord = '';
      clearTimeout(this._contextualTimer);
      this._contextualTimer = setTimeout(() => {
        const preds = this.engine.generateContextualSuggestions();
        this.updateSuggestions(
          preds.map((w) => ({ word: w, language: 'KREYOL' })),
          false
        );
      }, 100);
    }

    selectSuggestion(word) {
      if (this.currentWord) {
        this.screenText = this.screenText.slice(0, -this.currentWord.length);
      }
      this.screenText += word + ' ';
      this.currentWord = word;
      // Une proposition choisie porte déjà ses accents : rien à rétablir, et
      // rien à défaire au retour arrière qui suivrait.
      this.derniereRestauration = null;
      this.motRefuse = null;
      this.instantDernierEspace = 0;
      this.finalizeCurrentWord();
      this.renderScreen();
      this.handleAutoCapitalization();
      this.renderKeyboard();
    }

    shouldAutoCapitalize() {
      const text = this.screenText;
      if (!text || !text.trim()) return true;
      let lastIdx = -1;
      for (let i = text.length - 1; i >= 0; i--) {
        if ('.!?'.includes(text[i])) {
          lastIdx = i;
          break;
        }
      }
      if (lastIdx !== -1) {
        const after = text.slice(lastIdx + 1);
        if (!after.trim()) return true;
      }
      return false;
    }

    shouldCapitalize() {
      return this.isCapsLock || this.isCapitalMode || this.shouldAutoCapitalize();
    }

    handleAutoCapitalization() {
      if (this.shouldAutoCapitalize()) {
        this.isCapitalMode = true;
      } else if (this.isCapitalMode && !this.isCapsLock) {
        this.isCapitalMode = false;
      }
    }

    // ---- rendu écran / suggestions ----

    renderScreen() {
      this.els.screenText.textContent = this.screenText;
      this.els.screenText.appendChild(this.els.caret);
      this.els.placeholder.style.display = this.screenText ? 'none' : 'block';
      this.els.screen.scrollTop = this.els.screen.scrollHeight;
    }

    updateSuggestions(list, labeled) {
      const kreyol = list.filter((s) => s.language === 'KREYOL');
      const french = list.filter((s) => s.language === 'FRENCH');
      this.renderSuggRow(this.els.rowKreyol, kreyol, labeled ? 'KR' : null);
      this.renderSuggRow(this.els.rowFrench, french, labeled ? 'FR' : null);
      this.els.rowFrench.style.visibility = french.length ? 'visible' : 'hidden';
    }

    renderSuggRow(container, suggestions, label) {
      container.innerHTML = '';
      if (!suggestions.length) return;
      if (label) {
        const l = document.createElement('span');
        l.className = 'sugg-label';
        l.textContent = label;
        container.appendChild(l);
      }
      suggestions.forEach((s) => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'chip chip-' + s.language.toLowerCase();
        btn.textContent = s.word;
        btn.addEventListener('click', () => this.selectSuggestion(s.word));
        container.appendChild(btn);
      });
    }

    reset() {
      this.screenText = '';
      this.currentWord = '';
      this.isCapitalMode = false;
      this.isCapsLock = false;
      this.isNumericMode = false;
      this.isEmojiMode = false;
      this.derniereRestauration = null;
      this.motRefuse = null;
      this.instantDernierEspace = 0;
      this.engine.clearHistory();
      this.dismissAccentPopup();
      this.updateSuggestions([], false);
      this.renderScreen();
      this.renderKeyboard();
    }
  }

  window.KreyolKeyboardSimulator = KeyboardSimulator;
})();
