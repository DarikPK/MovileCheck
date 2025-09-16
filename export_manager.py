import pandas as pd
from datetime import datetime

def export_history_to_excel(history_data, output_path):
    """
    Exporta el historial de búsquedas a un archivo Excel.
    """
    if not history_data:
        return False

    processed_data = []
    for entry in history_data:
        doc_number = entry.get("documento", "N/A")
        result = entry.get("resultado", {})
        persona = result.get("persona", {})
        telefonos = result.get("telefonos", [])

        if not isinstance(persona, dict): persona = {}

        flat_row = {
            "DNI de Contacto": doc_number,
            "Persona": persona.get("nombre_completo", "No encontrado"),
            "Edad": persona.get("edad", "N/A"),
            "Dirección": persona.get("direccion", "N/A"),
            "Departamento": persona.get("departamento", "N/A"),
            "Provincia": persona.get("provincia", "N/A"),
            "Distrito": persona.get("distrito", "N/A"),
            "Telefonos": "; ".join(telefonos) if telefonos else "No se encontraron teléfonos",
            "Fecha de Consulta": datetime.fromisoformat(entry.get("timestamp")).strftime('%Y-%m-%d %H:%M:%S')
        }
        processed_data.append(flat_row)

    try:
        df = pd.DataFrame(processed_data)
        if not output_path.lower().endswith('.xlsx'):
            output_path += '.xlsx'

        df.to_excel(output_path, index=False, engine='openpyxl')
        return True
    except Exception as e:
        print(f"Error al exportar a Excel: {e}")
        return False
