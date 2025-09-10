from datasets import load_dataset
from collections import Counter
import json
import re

# Charger le dataset
dataset = load_dataset("POTOMITAN/potomitan-gcf-fr-translation")

# Extraire les phrases créoles
sentences = [row["gcf"] for row in dataset["train"]]

# Tokenisation simple
words = []
for sent in sentences:
    tokens = re.findall(r"[a-zA-Zòéèùà]+", sent.lower())
    words.extend(tokens)

# Compter les fréquences
counter = Counter(words)

# Garder les 2000 plus fréquents
most_common = counter.most_common(2000)

# Sauvegarde
with open("creole_dict.json", "w", encoding="utf-8") as f:
    json.dump(most_common, f, ensure_ascii=False, indent=2)

print("✅ creole_dict.json généré")
