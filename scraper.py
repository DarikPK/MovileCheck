import time
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.common.exceptions import TimeoutException, NoSuchElementException

class Scraper:
    """
    Clase para manejar las interacciones de web scraping con Selenium.
    """
    def __init__(self, headless=False):
        """
        Inicializa el WebDriver de Selenium.
        """
        options = webdriver.ChromeOptions()
        options.add_argument("--disable-blink-features=AutomationControlled")
        options.add_argument("--disable-save-password-bubble")
        options.add_argument("--disable-password-manager-reauthentication")
        options.add_argument("--window-size=1920,1080")
        if headless:
            options.add_argument("--headless=new")

        self.driver = webdriver.Chrome(options=options)
        self.wait = WebDriverWait(self.driver, 20)

    def login(self, user, password):
        """
        Realiza el proceso de login en la página.
        """
        try:
            print("Accediendo a la página de login...")
            self.driver.get("http://161.132.216.88/")

            usuario_input = self.wait.until(
                EC.presence_of_element_located((By.XPATH, '//input[@type="email"]'))
            )
            usuario_input.send_keys(user)

            clave_input = self.driver.find_element(By.XPATH, '//input[@type="password"]')
            clave_input.send_keys(password)

            self.driver.find_element(By.XPATH, '//button[contains(text(),"Ingresar")]').click()
            print("Login exitoso. Esperando página de búsqueda...")

            # Confirmar que la página de búsqueda ha cargado
            self.wait.until(
                EC.presence_of_element_located((By.NAME, "Documento"))
            )
            print("Página de búsqueda cargada.")
            return True, "Login exitoso"
        except Exception as e:
            print(f"Error durante el login: {e}")
            return False, f"Error en login: {e}"

    def search_document(self, doc_number):
        """
        Busca un DNI o RUC y extrae los datos.
        """
        try:
            print(f"Buscando documento: {doc_number}")
            loading_xpath = "//*[contains(text(), 'Cargando') or contains(text(), 'espere')]"

            # Esperar a que desaparezca cualquier mensaje de "Cargando" previo
            try:
                self.wait.until(
                    EC.invisibility_of_element_located((By.XPATH, loading_xpath))
                )
            except TimeoutException:
                print(f"Advertencia: El popup de carga previo no desapareció.")

            # Ingresar DNI/RUC
            dni_input = self.wait.until(
                EC.presence_of_element_located((By.NAME, "Documento"))
            )
            dni_input.clear()
            dni_input.send_keys(doc_number)

            buscar_button = self.wait.until(
                EC.element_to_be_clickable((By.XPATH, '//button[contains(text(),"Buscar")]'))
            )
            buscar_button.click()

            # Esperar a que el nuevo "Cargando" desaparezca
            try:
                self.wait.until(
                    EC.invisibility_of_element_located((By.XPATH, loading_xpath))
                )
            except TimeoutException:
                print(f"Advertencia: El mensaje 'Cargando' no desapareció tras la búsqueda.")

            return self.extract_data(doc_number)

        except Exception as e:
            print(f"Error buscando el documento {doc_number}: {e}")
            return {"error": f"Error al buscar: {e}"}

    def extract_data(self, doc_number):
        """
        Extrae los datos de las tablas de Personas y Teléfonos.
        """
        results = {
            "persona": {},
            "telefonos": [],
            "error": None
        }

        # Extraer datos de la tabla de Personas
        try:
            tabla_personas = self.wait.until(
                EC.presence_of_element_located(
                    (By.XPATH, '//div[@class="card" and .//h3[text()="Personas"]]//tbody')
                )
            )
            # Pequeña pausa para asegurar que el contenido de la tabla se actualice
            time.sleep(0.5)

            row_persona = tabla_personas.find_element(By.TAG_NAME, "tr")
            cols = row_persona.find_elements(By.TAG_NAME, "td")

            if "No se encontraron resultados" in row_persona.text:
                 results["persona"] = {"error": "No se encontraron datos de persona"}
            elif len(cols) >= 9:
                results["persona"] = {
                    "nombre_completo": f"{cols[0].text.strip()} {cols[1].text.strip()} {cols[2].text.strip()}",
                    "edad": cols[4].text.strip(),
                    "direccion": cols[5].text.strip(),
                    "departamento": cols[6].text.strip(),
                    "provincia": cols[7].text.strip(),
                    "distrito": cols[8].text.strip(),
                }
            else:
                results["persona"] = {"error": "La tabla de persona no tiene suficientes columnas."}
        except (TimeoutException, NoSuchElementException):
            print(f"No se encontró la tabla de Personas para el DNI {doc_number}")
            results["persona"] = {"error": "No se encontraron datos de persona"}

        # Extraer datos de la tabla de Teléfonos
        try:
            tel_table_body = self.wait.until(
                EC.presence_of_element_located(
                    (By.XPATH, '//div[@class="card" and .//h3[text()="Teléfonos"]]//tbody')
                )
            )
            time.sleep(0.5)

            rows = tel_table_body.find_elements(By.TAG_NAME, "tr")
            if rows and "No se encontraron resultados" in rows[0].text:
                results["telefonos"] = ["No se encontraron teléfonos"]
            else:
                telefonos_list = []
                for r in rows:
                    cols = r.find_elements(By.TAG_NAME, "td")
                    if len(cols) >= 3:
                        telefono = cols[0].text.strip()
                        periodo = cols[2].text.strip()
                        telefonos_list.append(f"{telefono} ({periodo})")
                results["telefonos"] = telefonos_list if telefonos_list else ["No se encontraron teléfonos"]
        except (TimeoutException, NoSuchElementException):
            print(f"No se encontró la tabla de Teléfonos para el DNI {doc_number}")
            results["telefonos"] = ["No se encontraron teléfonos"]

        return results

    def close(self):
        """
        Cierra el navegador y termina la sesión de WebDriver.
        """
        print("Cerrando navegador.")
        self.driver.quit()
