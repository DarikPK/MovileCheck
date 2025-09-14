import pandas as pd
from datetime import datetime

def export_history_to_excel(history_data, output_path):
    """
    Exporta el historial de búsquedas a un archivo Excel.

    Args:
        history_data (list): La lista de entradas del historial.
        output_path (str): La ruta de archivo seleccionada por el usuario para guardar.

    Returns:
        bool: True si la exportación fue exitosa, False en caso contrario.
    """
    if not history_data:
        print("No hay datos en el historial para exportar.")
        return False

    processed_data = []
    for entry in history_data:
        doc_number = entry.get("documento", "N/A")
        result = entry.get("resultado", {})
        persona = result.get("persona", {})
        telefonos = result.get("telefonos", [])

        # Asegurarse de que persona sea un diccionario antes de acceder a sus claves
        if not isinstance(persona, dict):
            persona = {}

        # Aplanar los datos para que encajen en una fila de Excel
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
        # Asegurarse de que el path termine en .xlsx
        if not output_path.lower().endswith('.xlsx'):
            output_path += '.xlsx'

        df.to_excel(output_path, index=False, engine='openpyxl')
        print(f"Historial exportado exitosamente a {output_path}")
        return True
    except Exception as e:
        print(f"Error al exportar el historial a Excel: {e}")
        return False
