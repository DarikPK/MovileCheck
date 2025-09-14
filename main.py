import flet as ft
import threading
from datetime import datetime
from scraper import Scraper
import history_manager
import export_manager

def main(page: ft.Page):
    page.title = "Buscador de Clientes"
    page.theme_mode = ft.ThemeMode.LIGHT
    page.window_width = 500
    page.window_height = 700
    page.vertical_alignment = ft.MainAxisAlignment.START
    page.horizontal_alignment = ft.CrossAxisAlignment.CENTER

    # --- Estado de la aplicación ---
    last_search_data = {}

    # --- Gestión del Scraper y Archivos ---
    def get_scraper():
        scraper = page.session.get("scraper")
        if scraper is None:
            # Poner headless=True para que el navegador no sea visible
            scraper = Scraper(headless=True)
            page.session.set("scraper", scraper)
        return scraper

    def close_scraper(e):
        scraper = page.session.get("scraper")
        if scraper:
            scraper.close()
            print("Navegador cerrado correctamente.")

    page.on_disconnect = close_scraper

    def save_file_result(e: ft.FilePickerResultEvent):
        if e.path:
            history = history_manager.load_history()
            if not history:
                show_snackbar("No hay historial para exportar.", color="orange")
                return

            success = export_manager.export_history_to_excel(history, e.path)
            if success:
                show_snackbar(f"Historial exportado exitosamente.")
            else:
                show_snackbar("Ocurrió un error al exportar el archivo.", color="red")
        else:
            show_snackbar("Operación de guardado cancelada.", color="orange")

    file_picker = ft.FilePicker(on_result=save_file_result)
    page.overlay.append(file_picker)

    # --- Controles y Lógica de la UI ---
    search_input = ft.TextField(label="Ingrese DNI o RUC", width=300, text_align=ft.TextAlign.CENTER, autofocus=True)
    results_view_content = ft.Column(spacing=15, width=400, scroll=ft.ScrollMode.ADAPTIVE, horizontal_alignment=ft.CrossAxisAlignment.CENTER)

    def show_alert(message):
        dlg = ft.AlertDialog(title=ft.Text("Alerta"), content=ft.Text(message)); page.dialog = dlg; dlg.open = True; page.update()

    def show_snackbar(message, color="green"):
        page.snack_bar = ft.SnackBar(content=ft.Text(message), bgcolor=color); page.snack_bar.open = True; page.update()

    # --- Lógica de Búsqueda e Historial ---
    def save_to_history(e):
        doc_number = last_search_data.get("doc_number")
        data = last_search_data.get("data")
        if doc_number and data and not data.get("error"):
            if history_manager.save_entry(doc_number, data): show_snackbar("Resultado guardado en el historial.")
            else: show_snackbar("Error al guardar en el historial.", color="red")
        else: show_snackbar("No hay un resultado válido para guardar.", color="orange")

    def re_search_from_history(e, doc_number):
        """Dispara una nueva búsqueda desde un botón del historial."""
        search_input.value = doc_number
        search_click(e)

    def search_click(e):
        doc_number = search_input.value.strip()
        if len(doc_number) == 7 and doc_number.isdigit(): doc_number = "0" + doc_number; search_input.value = doc_number
        elif not ((len(doc_number) == 8 or len(doc_number) == 11) and doc_number.isdigit()):
            show_alert("El DNI debe tener 8 dígitos y el RUC 11."); return
        page.go(f"/results/{doc_number}")
        threading.Thread(target=run_search_and_update_ui, args=(doc_number,), daemon=True).start()

    def run_search_and_update_ui(doc_number):
        results_view_content.controls.clear()
        results_view_content.controls.append(ft.ProgressRing())
        results_view_content.controls.append(ft.Text("Buscando, por favor espere..."))
        if page.route.startswith("/results"): page.update()

        scraper = get_scraper()
        if not page.session.get("logged_in"):
            success, message = scraper.login("Prueba4", "Prueba4")
            if not success:
                results_view_content.controls.clear(); results_view_content.controls.append(ft.Text(f"Error de Login: {message}", color="red"))
                if page.route.startswith("/results"): page.update(); return
            page.session.set("logged_in", True)

        data = scraper.search_document(doc_number)
        last_search_data["doc_number"] = doc_number; last_search_data["data"] = data

        results_view_content.controls.clear()
        if data.get("error"): results_view_content.controls.append(ft.Text(f"Error: {data['error']}", color="red", size=16))

        persona = data.get("persona", {})
        if persona and not persona.get("error"):
            results_view_content.controls.extend([ft.Text("Datos de la Persona", weight=ft.FontWeight.BOLD, size=20), ft.Text(f"Nombre: {persona.get('nombre_completo', 'N/A')}"), ft.Text(f"Edad: {persona.get('edad', 'N/A')}"), ft.Text(f"Dirección: {persona.get('direccion', 'N/A')}"), ft.Text(f"Ubicación: {persona.get('distrito')}, {persona.get('provincia')}, {persona.get('departamento')}")])
        else: results_view_content.controls.append(ft.Text("No se encontraron datos de persona.", color="orange"))

        telefonos = data.get("telefonos", [])
        results_view_content.controls.append(ft.Text("Teléfonos", weight=ft.FontWeight.BOLD, size=20, margin=ft.margin.only(top=15)))
        if telefonos and "No se encontraron teléfonos" not in telefonos:
            for tel in telefonos: results_view_content.controls.append(ft.Text(tel))
        else: results_view_content.controls.append(ft.Text("No se encontraron teléfonos.", color="orange"))

        results_view_content.controls.append(ft.ElevatedButton("Guardar en Historial", on_click=save_to_history, icon=ft.icons.SAVE, margin=ft.margin.only(top=20)))
        if page.route.startswith("/results"): page.update()

    # --- Vistas (Pantallas) ---
    def get_main_view():
        return ft.View("/",[ft.AppBar(title=ft.Text("Buscador de Clientes"), bgcolor=ft.colors.SURFACE_VARIANT), ft.Container(height=50), ft.Text("Seleccione una opción:", size=24, weight=ft.FontWeight.BOLD), ft.Container(height=20), ft.ElevatedButton("Buscar por DNI / RUC", on_click=lambda _: page.go("/search"), width=250, height=50, icon=ft.icons.SEARCH), ft.ElevatedButton("Ver Historial", on_click=lambda _: page.go("/history"), width=250, height=50, icon=ft.icons.HISTORY)], vertical_alignment=ft.MainAxisAlignment.START, horizontal_alignment=ft.CrossAxisAlignment.CENTER, spacing=25)
    def get_search_view():
        return ft.View("/search",[ft.AppBar(title=ft.Text("Buscar Documento"), bgcolor=ft.colors.SURFACE_VARIANT), ft.Container(height=50), ft.Text("Ingrese el número de documento", size=20), search_input, ft.ElevatedButton("Buscar", on_click=search_click, icon=ft.icons.FIND_IN_PAGE, width=200, height=50)], vertical_alignment=ft.MainAxisAlignment.START, horizontal_alignment=ft.CrossAxisAlignment.CENTER, spacing=25)
    def get_results_view(doc_number):
        return ft.View(f"/results/{doc_number}",[ft.AppBar(title=ft.Text(f"Resultados para {doc_number}"), bgcolor=ft.colors.SURFACE_VARIANT), ft.Container(height=20), results_view_content], vertical_alignment=ft.MainAxisAlignment.START, horizontal_alignment=ft.CrossAxisAlignment.CENTER, scroll=ft.ScrollMode.ADAPTIVE)
    def get_history_view():
        history_entries = history_manager.load_history()
        history_list_view = ft.ListView(expand=True, spacing=10, padding=20)
        if not history_entries:
            history_list_view.controls.append(ft.Text("El historial está vacío.", text_align=ft.TextAlign.CENTER, size=18))
        else:
            for entry in history_entries:
                persona_info = entry.get("resultado", {}).get("persona", {})
                nombre = persona_info.get('nombre_completo', 'N/A') if persona_info else 'N/A'
                fecha = datetime.fromisoformat(entry.get("timestamp")).strftime('%d/%m/%Y %H:%M')
                doc_number = entry.get('documento')
                card = ft.Card(content=ft.Container(padding=15, content=ft.Column([
                    ft.Row([
                        ft.Text(f"Documento: {doc_number}", weight=ft.FontWeight.BOLD, size=16),
                        ft.IconButton(icon=ft.icons.REFRESH, on_click=lambda e, d=doc_number: re_search_from_history(e, d), tooltip="Reconsultar")
                    ], alignment=ft.MainAxisAlignment.SPACE_BETWEEN),
                    ft.Text(f"Nombre: {nombre}"),
                    ft.Text(f"Fecha: {fecha}", size=12, color=ft.colors.GREY_700)
                ])))
                history_list_view.controls.append(card)
        return ft.View("/history",[
            ft.AppBar(title=ft.Text("Historial de Búsquedas"), bgcolor=ft.colors.SURFACE_VARIANT),
            ft.Column([
                ft.Container(content=ft.ElevatedButton("Exportar Historial a Excel", icon=ft.icons.DOWNLOAD, on_click=lambda _: file_picker.save_file(dialog_title="Guardar Historial", file_name=f"historial_{datetime.now().strftime('%Y%m%d')}.xlsx", allowed_extensions=["xlsx"])), alignment=ft.alignment.center, padding=10),
                history_list_view
            ], expand=True, horizontal_alignment=ft.CrossAxisAlignment.CENTER)],
            scroll=ft.ScrollMode.ADAPTIVE)

    # --- Navegación ---
    def route_change(route):
        page.views.clear(); page.views.append(get_main_view())
        if page.route.startswith("/search"): page.views.append(get_search_view())
        elif page.route.startswith("/results"): page.views.append(get_results_view(page.route.split("/")[-1]))
        elif page.route.startswith("/history"): page.views.append(get_history_view())
        page.update()
    def view_pop(view):
        page.views.pop(); top_view = page.views[-1]; page.go(top_view.route)
    page.on_route_change = route_change; page.on_view_pop = view_pop
    page.go("/")

if __name__ == "__main__":
    ft.app(target=main)
