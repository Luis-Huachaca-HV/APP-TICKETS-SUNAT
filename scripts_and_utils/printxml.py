import xml.etree.ElementTree as ET
import bluetooth
import time

def extraer_datos(xml_path):
    print("[*] Leyendo XML...")
    ns = {
        "cbc": "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",
        "cac": "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
    }

    tree = ET.parse(xml_path)
    root = tree.getroot()

    empresa = root.find(".//cac:AccountingSupplierParty//cbc:RegistrationName", ns).text.strip()
    ruc = root.find(".//cac:AccountingSupplierParty//cbc:ID", ns).text.strip()
    fecha = root.find(".//cbc:IssueDate", ns).text.strip()

    cliente = root.find(".//cac:AccountingCustomerParty//cbc:RegistrationName", ns)
    cliente = cliente.text.strip() if cliente is not None else "-"

    total = root.find(".//cac:LegalMonetaryTotal//cbc:PayableAmount", ns).text.strip()
    direccion = root.find(".//cac:AccountingSupplierParty//cac:Party//cac:PhysicalLocation//cac:Address//cbc:StreetName", ns)
    direccion = direccion.text.strip() if direccion is not None else "-"

    telefono = root.find(".//cac:AccountingSupplierParty//cac:Party//cac:Contact//cbc:Telephone", ns)
    telefono = telefono.text.strip() if telefono is not None else "-"


    items = []
    for item in root.findall(".//cac:InvoiceLine", ns):
        desc = item.find(".//cac:Item//cbc:Description", ns).text.strip()
        cantidad = item.find(".//cbc:InvoicedQuantity", ns).text.strip()
        precio = item.find(".//cac:Price//cbc:PriceAmount", ns).text.strip()
        total_item = item.find(".//cbc:LineExtensionAmount", ns).text.strip()
        items.append((desc, cantidad, precio, total_item))

    return {

  
        "empresa": empresa,
        "ruc": ruc,
        "fecha": fecha,
        "cliente": cliente,
        "total": total,
        "items": items,
        "direccion": direccion,
        "telefono": telefono
    }

def limpiar(texto):
    # Elimina acentos y reemplaza puntos por espacio o nada
    texto = texto.replace('.', '')  # quitar puntos
    return texto.encode("ascii", errors="ignore").decode("ascii")

def generar_escpos_texto(boleta_data):
    ESC = b'\x1b'
    CENTER = ESC + b'a' + b'\x01'
    LEFT = ESC + b'a' + b'\x00'
    BOLD_ON = ESC + b'E' + b'\x01'
    BOLD_OFF = ESC + b'E' + b'\x00'

    line_sep = b"-" * 32 + b"\n"
    ticket = b""

    ticket += CENTER + f"RUC: {boleta_data['ruc']}\n".encode("latin-1", errors="ignore")
    ticket += LEFT + b"HAMPIY INVERSIONES.S.R.L.\n"
    ticket += CENTER + limpiar(f"{boleta_data['direccion']}\n").encode("latin-1", errors="ignore")
    ticket += CENTER + limpiar(f"Tel: {boleta_data['telefono']}\n").encode("latin-1", errors="ignore")
    ticket += line_sep

    ticket += limpiar(f"Cliente: {boleta_data['cliente']}\n").encode("latin-1", errors="ignore")

    # 🔥 Línea crítica: sanitizamos y partimos si es larga
    #ticket += f"RUC: {boleta_data['ruc']}\n".encode("latin-1", errors="ignore")
    ticket += line_sep

    total = 0
    for desc, cantidad, precio, total_item in boleta_data["items"]:
        desc = limpiar(desc[:16])
        cantidad = float(cantidad)
        precio = float(precio)
        total_item = float(total_item)
        total += total_item
        linea = f"{desc:16}{int(cantidad):>2} x {precio:>5.2f} = {total_item:>6.2f}\n"
        ticket += linea.encode("latin-1", errors="ignore")

    subtotal = total / 1.18
    igv = total - subtotal

    ticket += line_sep
    ticket += f"SUBTOTAL:       S/ {subtotal:>7.2f}\n".encode()
    ticket += f"IGV (18%):      S/ {igv:>7.2f}\n".encode()
    ticket += f"TOTAL:          S/ {total:>7.2f}\n".encode()
    ticket += line_sep
    ticket += CENTER + b"Gracias por su compra!\n"
    ticket += CENTER + b"Vuelva pronto :)\n"
    ticket += b"\n\n\n\n\n"

    return ticket

def enviar_a_impresora(mac, datos):
    print(f"[*] Conectando a {mac}...")
    try:
        sock = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
        sock.connect((mac, 1))
        print("[*] Enviando en bloques de 64 bytes...")
        for i in range(0, len(datos), 64):
            bloque = datos[i:i+64]
            print(f"[DEBUG] Bloque {i}-{i+64}")
            sock.send(bloque)
            time.sleep(0.1)
        print("✅ Boleta enviada correctamente.")
    except Exception as e:
        import traceback
        traceback.print_exc()
    finally:
        try:
            sock.close()
        except:
            pass

if __name__ == "__main__":
    ruta_xml = "Boleta-20606661119-EB01-4 (1).xml"
    mac_bt58d = "86:67:7A:AC:19:E5"

    print("[*] Iniciando impresión desde XML...")
    datos = extraer_datos(ruta_xml)
    print("[*] Datos extraídos:")
    print(datos)
    escpos = generar_escpos_texto(datos)
    print(f"[*] Total bytes generados: {len(escpos)}")
    enviar_a_impresora(mac_bt58d, escpos)
