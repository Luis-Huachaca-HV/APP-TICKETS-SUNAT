import time
import bluetooth

def limpiar_buffer(mac_address):
    # Comandos ESC/POS comunes de reinicio
    ESC = b'\x1b'
    GS = b'\x1d'

    RESET = ESC + b'@'         # Reset printer
    CANCEL_PRINT = GS + b'V' + b'\x00'  # Cancelar (opcional)
    FEED = b'\n' * 4           # Alimentar papel (limpia salida)
    CUT = GS + b'V' + b'\x00'  # Corte (si tuviera cutter)

    limpieza = RESET + FEED + CANCEL_PRINT

    print(f"[*] Intentando conectar a {mac_address} para limpiar buffer...")
    try:
        sock = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
        sock.connect((mac_address, 1))
        sock.send(limpieza)
        time.sleep(0.5)
        sock.close()
        print("✅ Buffer limpiado correctamente.")
    except Exception as e:
        import traceback
        print("❌ Error al limpiar el buffer:")
        traceback.print_exc()
    finally:
        try:
            sock.close()
        except:
            pass
        time.sleep(1.0)  # Esperar a que libere RFCOMM

if __name__ == "__main__":
    mac_bt58d = "86:67:7A:AC:19:E5"
    limpiar_buffer(mac_bt58d)
