/* Inscription à la newsletter, posée dans chaque <div class="inscription-lettre">.
 * Repris de LuxKeyb (docs/assets/inscription-lettre.js), en français seul et
 * aux couleurs de la palette de communication (theme.css).
 *
 * L'adresse part directement au formulaire Brevo de Klavyé Kréyòl
 * (« confirmation simple » : le contact entre dans la liste dès l'envoi). Le
 * serveur de Brevo accepte les envois venant de famibelle.github.io et répond
 * en JSON ({"success":true}), ce qui permet d'afficher la réponse sur place.
 *
 * FORMULAIRE est le seul réglage : le lien « sibforms.com/v2/serve/… » du
 * formulaire Brevo. Tant qu'il est vide, le bloc reste invisible. Ne pas y
 * mettre celui de LuxKeyb : les inscrits iraient dans la liste luxembourgeoise. */
(function () {
  "use strict";

  var FORMULAIRE = "";

  var NB = "\u00a0";
  var t = {
    titre: "Recevez la prochaine lettre",
    chapo: "Les nouveautés du clavier kréyòl, directement dans votre boîte mail. Une lettre par mois, pas plus.",
    champ: "Votre adresse e-mail",
    bouton: "Je m'inscris",
    mention: "Gratuit. Chaque lettre a un lien pour vous désabonner.",
    merci: "Mèsi, c'est fait" + NB + "! Vous recevrez la prochaine lettre dans votre boîte mail.",
    erreur: "L'inscription n'a pas abouti. Vérifiez votre adresse et réessayez."
  };

  var STYLE =
    ".inscription-lettre{font-family:inherit;background:var(--card,#fff);color:var(--ink,#1C2624);border:1px solid var(--line,#DCDAD3);border-top:4px solid var(--hibiscus,#C94A3B);border-radius:10px;padding:20px 22px;margin:8px 0}" +
    ".inscription-lettre h2{font:600 22px/1.2 \"Fredoka\",sans-serif;margin:0 0 4px;color:inherit}" +
    ".inscription-lettre p{margin:0;color:var(--ink-soft,#4B5750);font-size:16px;line-height:1.5}" +
    ".inscription-lettre form{display:flex;flex-wrap:wrap;gap:10px;margin:14px 0 10px}" +
    ".inscription-lettre form[hidden]{display:none}" +
    ".inscription-lettre input[type=email]{flex:1 1 220px;min-width:0;font:inherit;font-size:16px;line-height:1.2;padding:12px 14px;border:1px solid var(--line,#DCDAD3);border-radius:8px;background:var(--paper,#fff);color:var(--ink,#1C2624)}" +
    ".inscription-lettre input[type=email]:focus{outline:3px solid var(--sun,#E3AE5E);outline-offset:1px}" +
    ".inscription-lettre button{flex:0 0 auto;font:600 16px/1 \"Fredoka\",sans-serif;padding:12px 20px;border:0;border-radius:99px;background:var(--sea,#0E6E76);color:#fff;cursor:pointer}" +
    ".inscription-lettre button[disabled]{opacity:.6;cursor:wait}" +
    ".inscription-lettre .il-piege{position:absolute;left:-9999px;width:1px;height:1px;overflow:hidden}" +
    ".inscription-lettre .il-mention{font-size:14px}" +
    ".inscription-lettre .il-reponse{font-weight:700;margin-top:8px}" +
    ".inscription-lettre .il-reponse:empty{display:none}" +
    ".inscription-lettre .il-reponse.ok{color:var(--sea,#0E6E76)}" +
    ".inscription-lettre .il-reponse.ko{color:var(--hibiscus,#C94A3B)}";

  function monter(boite) {
    boite.innerHTML =
      "<h2></h2><p class=\"il-chapo\"></p>" +
      "<form novalidate><input type=\"email\" name=\"EMAIL\" autocomplete=\"email\" required>" +
      "<span class=\"il-piege\" aria-hidden=\"true\"><input type=\"text\" name=\"email_address_check\" tabindex=\"-1\" autocomplete=\"off\"></span>" +
      "<button type=\"submit\"></button></form>" +
      "<p class=\"il-mention\"></p><p class=\"il-reponse\" role=\"status\" aria-live=\"polite\"></p>";
    boite.querySelector("h2").textContent = t.titre;
    boite.querySelector(".il-chapo").textContent = t.chapo;
    var champ = boite.querySelector("input[type=email]");
    champ.placeholder = t.champ;
    champ.setAttribute("aria-label", t.champ);
    var bouton = boite.querySelector("button");
    bouton.textContent = t.bouton;
    boite.querySelector(".il-mention").textContent = t.mention;
    var reponse = boite.querySelector(".il-reponse");
    var form = boite.querySelector("form");

    function dire(texte, ok) {
      reponse.textContent = texte;
      reponse.className = "il-reponse " + (ok ? "ok" : "ko");
    }

    form.addEventListener("submit", function (e) {
      e.preventDefault();
      var adresse = champ.value.trim();
      if (!champ.checkValidity() || !adresse) { dire(t.erreur, false); champ.focus(); return; }
      var donnees = new URLSearchParams();
      donnees.append("EMAIL", adresse);
      donnees.append("email_address_check", form.elements.email_address_check.value);
      donnees.append("locale", "fr");
      bouton.disabled = true;
      fetch(FORMULAIRE + "?isAjax=1", { method: "POST", body: donnees })
        .then(function (r) { return r.ok ? r.json() : { success: false }; })
        .then(function (j) {
          if (j && j.success) { form.hidden = true; dire(t.merci, true); }
          else { dire(t.erreur, false); }
        })
        .catch(function () { dire(t.erreur, false); })
        .then(function () { bouton.disabled = false; });
    });
  }

  function demarrer() {
    var boites = document.querySelectorAll(".inscription-lettre");
    if (!boites.length || !FORMULAIRE) return;
    var s = document.createElement("style");
    s.textContent = STYLE;
    document.head.appendChild(s);
    for (var i = 0; i < boites.length; i++) monter(boites[i]);
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", demarrer);
  else demarrer();
})();
