import time
import bluetooth
from escpos.printer import Dummy
from PIL import Image
from pdf2image import convert_from_path

def convertir_pdf_a_imagen(ruta_pdf):
    imagenes = convert_from_path(ruta_pdf, dpi=100)
    imagen = imagenes[0].convert("L")
    imagen = imagen.resize((384, int(imagen.height * (384 / imagen.width))))
    imagen = imagen.convert("1")
    return imagen

def dividir_imagen(imagen, altura_parte=80):
    partes = []
    for y in range(0, imagen.height, altura_parte):
        parte = imagen.crop((0, y, imagen.width, min(y + altura_parte, imagen.height)))
        partes.append(parte)
    return partes

def generar_datos(parte, cortar=False):
    p = Dummy()
    p.image(parte)
    if cortar:
        p.cut()
    return p.output

def imprimir_conexion_unica(mac, partes):
    try:
        sock = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
        sock.connect((mac, 1))
        print("[✓] Conectado a la impresora.")

        for i, parte in enumerate(partes):
            cortar = i == len(partes) - 1
            print(f"  - Parte {i+1}/{len(partes)}")
            datos = generar_datos(parte, cortar=cortar)
            for j in range(0, len(datos), 128):
                sock.send(datos[j:j+128])
                time.sleep(0.15)
            #time.sleep(0.4)
        print("✅ Impresión completa.")
        sock.close()
    except Exception as e:
        import traceback
        traceback.print_exc()
        print("❌ Error durante impresión.")

if __name__ == "__main__":
    ruta = "2020-08-19-BOLETA-B001-1-20123456789_copy.pdf"
    mac = "86:67:7A:AC:19:E5"
    img = convertir_pdf_a_imagen(ruta)
    partes = dividir_imagen(img, altura_parte=80)
    imprimir_conexion_unica(mac, partes)
