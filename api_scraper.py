import requests
from bs4 import BeautifulSoup

class ApiScraper:
    """
    Scraper que interactúa directamente con los endpoints de la API web,
    sin necesidad de un navegador (Selenium).
    """
    def __init__(self):
        self.base_url = "http://161.132.216.88"
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
        })
        self.token = None

    def _get_token(self):
        """Obtiene el __RequestVerificationToken de la página de login."""
        try:
            response = self.session.get(self.base_url + "/")
            response.raise_for_status()
            soup = BeautifulSoup(response.text, 'html.parser')
            token_element = soup.find('input', {'name': '__RequestVerificationToken'})
            if token_element:
                self.token = token_element['value']
                return True
            return False
        except requests.exceptions.RequestException:
            return False

    def login(self, username, password):
        """Realiza el login y establece la cookie de sesión."""
        if not self._get_token():
            return False, "No se pudo obtener el token de verificación."

        login_data = {
            'Correo': username,
            'Clave': password,
            '__RequestVerificationToken': self.token
        }

        try:
            response = self.session.post(self.base_url + "/", data=login_data, allow_redirects=True)
            response.raise_for_status()
            if "Busqueda:" in response.text:
                return True, "Login exitoso."
            else:
                return False, "Credenciales incorrectas o fallo en el login."
        except requests.exceptions.RequestException as e:
            return False, f"Error de red durante el login: {e}"

    def _parse_results(self, html_content):
        """Parsea el HTML de resultados y lo convierte en un diccionario."""
        soup = BeautifulSoup(html_content, 'html.parser')
        results = {"persona": {}, "telefonos": []}

        # Parsear tabla de personas
        persona_table = soup.find('h3', class_='card-title', string='Personas')
        if persona_table:
            rows = persona_table.find_parent('div', class_='card').find_all('tr')
            if len(rows) > 1: # >1 para saltar el encabezado
                cols = rows[1].find_all('td')
                if "No se encontraron resultados" not in cols[0].text:
                    results["persona"] = {
                        "nombre_completo": f"{cols[2].text.strip()} {cols[0].text.strip()} {cols[1].text.strip()}",
                        "nacimiento": cols[3].text.strip(),
                        "edad": cols[4].text.strip(),
                        "direccion": cols[5].text.strip(),
                        "departamento": cols[6].text.strip(),
                        "provincia": cols[7].text.strip(),
                        "distrito": cols[8].text.strip(),
                    }

        # Parsear tabla de teléfonos
        telefonos_table = soup.find('h3', class_='card-title', string='Teléfonos')
        if telefonos_table:
            rows = telefonos_table.find_parent('div', class_='card').find_all('tr')
            for row in rows[1:]: # Saltar encabezado
                cols = row.find_all('td')
                if len(cols) >= 3 and "No se encontraron resultados" not in cols[0].text:
                    results["telefonos"].append(f"{cols[0].text.strip()} ({cols[2].text.strip()})")

        return results

    def search(self, doc_number, doc_type="DNI"):
        """Realiza una búsqueda y devuelve los resultados parseados."""
        if not self.token:
            return None, "Se requiere login primero."

        search_url = f"{self.base_url}/Busqueda/BusquedaBuscarDocumento"
        search_data = {'TipoDocumento': doc_type, 'Documento': doc_number}
        headers = {
            'Referer': f'{self.base_url}/Home/Index',
            'X-Requested-With': 'XMLHttpRequest',
            'RequestVerificationToken': self.token
        }

        try:
            response = self.session.post(search_url, data=search_data, headers=headers)
            response.raise_for_status()

            parsed_data = self._parse_results(response.text)
            return parsed_data, "Búsqueda exitosa."

        except requests.exceptions.RequestException as e:
            return None, f"Error de red durante la búsqueda: {e}"
