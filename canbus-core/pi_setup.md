# Raspberry Pi CAN Bus Installation (Dual MCP2515 HAT)

This document describes how to configure a Raspberry Pi with a **dual-channel MCP2515 CAN HAT** using SPI, resulting in two CAN interfaces: `can0` and `can1`.

The steps apply to **recent Raspberry Pi OS releases**, where the boot configuration file has moved to:

```
/boot/firmware/config.txt
```

---

## Hardware Assumptions

- Raspberry Pi (Pi 3, 4, 5, Zero 2 W)
- Dual MCP2515 CAN HAT
- MCP2515 controllers with **8 MHz crystal**
- Two separate interrupt (INT) lines
- Proper CAN bus wiring with **120 Ω termination at each end**

This configuration uses:
- SPI bus: `spi0`
- Chip selects: `CS0` and `CS1`
- Interrupt pins: GPIO 23 and GPIO 25

---

## Enable SPI and Dual Chip Select

Edit the boot configuration file:

```bash
sudo nano /boot/firmware/config.txt
```

Add the following:

```ini
# Enable SPI with two chip selects
dtoverlay=spi0-2cs

# CAN0 on SPI0 CS0 (spics=0) with its interrupt pin
dtoverlay=mcp2515-can0,oscillator=8000000,interrupt=23,spibus=0,spics=0

# CAN1 on SPI0 CS1 (spics=1) with a different interrupt pin
dtoverlay=mcp2515-can1,oscillator=8000000,interrupt=25,spibus=0,spics=1
```

### Notes

- **oscillator=8000000** must match the MCP2515 crystal on the HAT
- Each CAN controller **must have a unique interrupt pin**
- `spi0-2cs` is required for dual MCP2515 devices on the same SPI bus

Reboot:

```bash
sudo reboot
```

---

## Install CAN Utilities

```bash
sudo apt update
sudo apt install can-utils
```

---

## Bring Up CAN Interfaces (Manual)

Example using **500 kbit/s**:

```bash
sudo ip link set can0 up type can bitrate 500000
sudo ip link set can1 up type can bitrate 500000
```

Verify:

```bash
ip -details link show can0
ip -details link show can1
```

---

## Automatic CAN Interface Startup (systemd)

To ensure the CAN interfaces are automatically brought up on boot, create a systemd service.

### Create the service file

```bash
sudo nano /etc/systemd/system/canbus.service
```

```ini
[Unit]
Description=Bring up CAN bus interfaces
After=network.target
Wants=network.target

[Service]
Type=oneshot
RemainAfterExit=yes

ExecStart=/sbin/ip link set can0 up type can bitrate 500000
ExecStart=/sbin/ip link set can1 up type can bitrate 500000

ExecStop=/sbin/ip link set can0 down
ExecStop=/sbin/ip link set can1 down

[Install]
WantedBy=multi-user.target
```

### Enable and start the service

```bash
sudo systemctl daemon-reexec
sudo systemctl daemon-reload
sudo systemctl enable canbus.service
sudo systemctl start canbus.service
```

Verify:

```bash
systemctl status canbus.service
ip link show can0
ip link show can1
```

---

## Basic Test

```bash
candump can0
cansend can0 123#DEADBEEF
```

Repeat for `can1` as required.

---

## Loopback Test (No External Device)

```bash
sudo ip link set can0 down
sudo ip link set can0 up type can bitrate 500000 loopback on
```

---

## Termination Requirements

- Exactly two **120 Ω** termination resistors per CAN bus
- One at each physical end
- Enable onboard termination only if the HAT is at the end of the bus
- Each CAN channel is electrically independent

---

## Result

The system provides two standard SocketCAN interfaces:

- `can0`
- `can1`

These interfaces are suitable for:
- J1939
- NMEA 2000
- Diagnostics
- Protocol gateways and data ingestion
