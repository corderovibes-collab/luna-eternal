#!/usr/bin/env python3
"""Manda comandos al servidor y devuelve lo que respondio en el log.

La API de Pterodactyl no devuelve la salida del comando: solo lo encola. Asi
que se marca la posicion del log ANTES de mandar, y se lee lo nuevo despues.
"""
import time

import ptero


def marca():
    return len(ptero.leer("/logs/latest.log").splitlines())


def enviar(comandos, espera=1.5, ruido=True):
    """Manda comandos y devuelve las lineas nuevas del log."""
    if isinstance(comandos, str):
        comandos = [comandos]
    antes = marca()
    for c in comandos:
        ptero.comando(c)
        time.sleep(0.25)
    time.sleep(espera)
    nuevas = ptero.leer("/logs/latest.log").splitlines()[antes:]
    if not ruido:
        return nuevas
    fuera = ("Saving", "ThreadedAnvilChunkStorage", "Preparing", "Time elapsed")
    return [l for l in nuevas if not any(f in l for f in fuera)]


def imprimir(comandos, espera=1.5):
    for l in enviar(comandos, espera):
        # El log trae codigos de color de Minecraft; estorban al leer.
        print(l.replace("§", "&")[:160])
