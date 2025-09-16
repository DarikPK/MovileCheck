import json
import os
from datetime import datetime

HISTORY_FILE = "search_history.json"

def load_history():
    """
    Carga el historial de búsquedas desde el archivo JSON.
    Si el archivo no existe o está corrupto, devuelve una lista vacía.
    """
    if not os.path.exists(HISTORY_FILE):
        return []
    try:
        with open(HISTORY_FILE, "r", encoding="utf-8") as f:
            content = f.read()
            if not content:
                return []
            return json.loads(content)
    except (json.JSONDecodeError, IOError):
        return []

def save_entry(doc_number, data):
    """
    Guarda una nueva entrada de búsqueda en el archivo de historial.
    """
    history = load_history()

    new_entry = {
        "timestamp": datetime.now().isoformat(),
        "documento": doc_number,
        "resultado": data
    }

    history.insert(0, new_entry) # Añade al principio

    try:
        with open(HISTORY_FILE, "w", encoding="utf-8") as f:
            json.dump(history, f, ensure_ascii=False, indent=4)
        return True
    except IOError as e:
        print(f"Error al guardar el historial: {e}")
        return False
