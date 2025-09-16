import flet as ft
import threading
from datetime import datetime
from api_scraper import ApiScraper
import history_manager
import export_manager

def main(page: ft.Page):
    page.title = "Buscador de Clientes"
    page.theme_mode = ft.ThemeMode.LIGHT
    page.window_width = 400
    page.window_height = 750
    page.vertical_alignment = ft.MainAxisAlignment.START
    page.horizontal_alignment = ft.CrossAxisAlignment.CENTER

    # --- Estado de la App ---
    last_search_result = {}

    # --- Gestores ---
    def get_scraper():
        scraper = page.session.get("api_scraper")
        if scraper is None:
            scraper = ApiScraper()
            page.session.set("api_scraper", scraper)
        return scraper

    def save_file_result(e: ft.FilePickerResultEvent):
        if e.path:
            history = history_manager.load_history()
            if not history:
                show_snackbar("No hay historial para exportar.", color="orange"); return

            if export_manager.export_history_to_excel(history, e.path):
                show_snackbar("Historial exportado exitosamente.")
            else:
                show_snackbar("Error al exportar el archivo.", color="red")
        else:
            show_snackbar("Operación de guardado cancelada.", color="orange")

    file_picker = ft.FilePicker(on_result=save_file_result)
    page.overlay.append(file_picker)

    # --- Controles y Lógica de UI ---
    search_input = ft.TextField(label="Ingrese DNI o RUC", text_align=ft.TextAlign.CENTER, autofocus=True)
    results_container = ft.ListView(spacing=15, expand=True, auto_scroll=True)
    history_list_view = ft.ListView(expand=True, spacing=10, padding=20)
    loading_indicator = ft.Column([ft.ProgressRing(), ft.Text("Buscando...")], alignment=ft.MainAxisAlignment.CENTER, horizontal_alignment=ft.CrossAxisAlignment.CENTER, expand=True, visible=False)

    def show_snackbar(message, color="green"):
        page.snack_bar = ft.SnackBar(content=ft.Text(message), bgcolor=color); page.snack_bar.open = True; page.update()

    def save_to_history(e):
        doc = last_search_result.get("doc_number")
        data = last_search_result.get("data")
        if doc and data:
            if history_manager.save_entry(doc, data): show_snackbar("Resultado guardado.")
            else: show_snackbar("Error al guardar.", color="red")
        else: show_snackbar("No hay resultado para guardar.", color="orange")

    def re_search_from_history(e, doc_number):
        search_input.value = doc_number
        search_click(e)

    # --- Lógica de Búsqueda ---
    def search_click(e):
        doc_number = search_input.value.strip()
        if len(doc_number) == 7 and doc_number.isdigit(): doc_number = "0" + doc_number; search_input.value = doc_number
        elif not ((len(doc_number) == 8 or len(doc_number) == 11) and doc_number.isdigit()):
            show_snackbar("El DNI debe tener 8 dígitos y el RUC 11.", color="orange"); return
        page.go(f"/results/{doc_number}")

    def run_search_and_update_ui(doc_number):
        loading_indicator.visible = True; results_container.visible = False; page.update()

        scraper = get_scraper()
        if not page.session.get("logged_in"):
            success, message = scraper.login("Prueba4", "Prueba4")
            if not success:
                loading_indicator.visible = False
                results_container.controls.append(ft.Text(f"Error de Login: {message}", color="red")); results_container.visible = True; page.update(); return
            page.session.set("logged_in", True)

        data, message = scraper.search(doc_number)
        last_search_result["doc_number"] = doc_number; last_search_result["data"] = data

        loading_indicator.visible = False; results_container.controls.clear()

        if not data:
            results_container.controls.append(ft.Text(f"Error: {message}", color="red"))
        else:
            persona, telefonos = data.get("persona"), data.get("telefonos")
            if persona: results_container.controls.append(ft.Card(content=ft.Container(padding=15, content=ft.Column([ft.Text("Datos de la Persona", weight=ft.FontWeight.BOLD, size=18), ft.Text(f"Nombre: {persona.get('nombre_completo', 'N/A')}"), ft.Text(f"Edad: {persona.get('edad', 'N/A')}"), ft.Text(f"Dirección: {persona.get('direccion', 'N/A')}"), ft.Text(f"Ubicación: {persona.get('distrito')}, {persona.get('provincia')}, {persona.get('departamento')}")]))))
            else: results_container.controls.append(ft.Text("No se encontraron datos de persona."))
            if telefonos:
                phone_items = [ft.Text("Teléfonos", weight=ft.FontWeight.BOLD, size=18)] + [ft.Text(tel) for tel in telefonos]
                results_container.controls.append(ft.Card(content=ft.Container(padding=15, content=ft.Column(phone_items))))
            else: results_container.controls.append(ft.Text("No se encontraron teléfonos."))
            results_container.controls.append(ft.FilledButton("Guardar en Historial", icon=ft.icons.SAVE, on_click=save_to_history))

        results_container.visible = True; page.update()

    # --- Vistas (Pantallas) ---
    def get_main_view():
        return ft.View("/", [ft.AppBar(title=ft.Text("Buscador de Clientes"), center_title=True, bgcolor=ft.colors.SURFACE_VARIANT), ft.Container(height=50), ft.Icon(ft.icons.SEARCH, size=80, color=ft.colors.PRIMARY), ft.Text("Búsqueda de Clientes", size=28, weight=ft.FontWeight.BOLD), ft.Container(height=50), ft.FilledButton("Nueva Búsqueda", on_click=lambda _: page.go("/search"), icon=ft.icons.ADD, width=250, height=50), ft.ElevatedButton("Ver Historial", on_click=lambda _: page.go("/history"), icon=ft.icons.HISTORY, width=250, height=50)], vertical_alignment=ft.MainAxisAlignment.START, horizontal_alignment=ft.CrossAxisAlignment.CENTER, spacing=25)
    def get_search_view():
        return ft.View("/search", [ft.AppBar(title=ft.Text("Nueva Búsqueda"), center_title=True, bgcolor=ft.colors.SURFACE_VARIANT), ft.Container(height=50), ft.Text("Ingrese el número de DNI o RUC", size=18), search_input, ft.FilledButton("Buscar", on_click=search_click, icon=ft.icons.FIND_IN_PAGE, width=200, height=50)], vertical_alignment=ft.MainAxisAlignment.START, horizontal_alignment=ft.CrossAxisAlignment.CENTER, spacing=25)
    def get_results_view(doc_number):
        threading.Thread(target=run_search_and_update_ui, args=(doc_number,), daemon=True).start()
        return ft.View(f"/results/{doc_number}", [ft.AppBar(title=ft.Text(f"Resultados para {doc_number}"), center_title=True, bgcolor=ft.colors.SURFACE_VARIANT), loading_indicator, results_container], vertical_alignment=ft.MainAxisAlignment.START, horizontal_alignment=ft.CrossAxisAlignment.CENTER)
    def get_history_view():
        history_list_view.controls.clear()
        history_entries = history_manager.load_history()
        if not history_entries:
            history_list_view.controls.append(ft.Text("El historial está vacío.", text_align=ft.TextAlign.CENTER, size=18))
        else:
            for entry in history_entries:
                persona_info = entry.get("resultado", {}).get("persona", {})
                nombre = persona_info.get('nombre_completo', 'N/A') if persona_info else 'N/A'
                doc_number = entry.get('documento')
                card = ft.Card(content=ft.Container(padding=15, content=ft.Column([ft.Row([ft.Text(f"Documento: {doc_number}", weight=ft.FontWeight.BOLD, size=16), ft.IconButton(icon=ft.icons.REFRESH, tooltip="Reconsultar", on_click=lambda e, d=doc_number: re_search_from_history(e, d))], alignment=ft.MainAxisAlignment.SPACE_BETWEEN), ft.Text(f"Nombre: {nombre}"), ft.Text(f"Fecha: {datetime.fromisoformat(entry.get('timestamp')).strftime('%d/%m/%Y %H:%M')}", size=12, color=ft.colors.GREY_700)])))
                history_list_view.controls.append(card)
        return ft.View("/history", [ft.AppBar(title=ft.Text("Historial"), center_title=True, bgcolor=ft.colors.SURFACE_VARIANT), ft.Container(content=ft.FilledButton("Exportar a Excel", icon=ft.icons.DOWNLOAD, on_click=lambda _: file_picker.save_file(dialog_title="Guardar Historial", file_name=f"historial_{datetime.now().strftime('%Y%m%d')}.xlsx", allowed_extensions=["xlsx"])), alignment=ft.alignment.center, padding=10), ft.Divider(), history_list_view])

    # --- Navegación ---
    def route_change(route):
        page.views.clear(); page.views.append(get_main_view())
        if page.route.startswith("/search"): page.views.append(get_search_view())
        elif page.route.startswith("/results"): page.views.append(get_results_view(page.route.split("/")[-1]))
        elif page.route.startswith("/history"): page.views.append(get_history_view())
        page.update()
    def view_pop(view):
        page.views.pop(); page.go(page.views[-1].route)
    page.on_route_change = route_change; page.on_view_pop = view_pop
    page.go("/")

if __name__ == "__main__":
    ft.app(target=main)
