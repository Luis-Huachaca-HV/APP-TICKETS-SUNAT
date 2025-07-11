import bluetooth

print("🔍 Escaneando dispositivos Bluetooth...")
devices = bluetooth.discover_devices(duration=8, lookup_names=True)

if not devices:
    print("⚠️ No se encontró ningún dispositivo Bluetooth.")
else:
    for addr, name in devices:
        print(f"📱 {name} - {addr}")
