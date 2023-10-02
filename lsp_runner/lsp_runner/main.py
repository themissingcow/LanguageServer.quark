from __future__ import annotations

import argparse
import asyncio
import logging
import os
import signal
import sys
from asyncio.streams import StreamReader
from threading import Event, Thread

DEFAULT_SCLANG_LSP_SERVERPORT = 57210
DEFAULT_SCLANG_LSP_CLIENTPORT = 57211
LOCALHOST = "127.0.0.1"

logger = logging.getLogger(__name__)
logger.setLevel(logging.DEBUG)
logging.basicConfig(filename="output.log", filemode="w", level=logging.DEBUG)


class StdinThread(Thread):
    def __init__(self, on_stdin_received):
        super().__init__()
        self._stop_event = Event()
        self._on_received = on_stdin_received

    def run(self):
        while not self._stop_event.is_set():
            try:
                line = sys.stdin.readline()

                if line.strip() == "exit":
                    break

                if not line:
                    continue

                self._on_received(line)

            except KeyboardInterrupt:
                break

    def close(self):
        self._stop_event.set()


class UDPProtocol(asyncio.DatagramProtocol):
    def connection_made(self, transport):
        logger.info("UDP SERVER: connection made")
        self.transport = transport

    def datagram_received(self, data, addr):
        print(data.decode())

    def error_received(self, exc):
        print(f"UDP error: {exc}")


class LSPRunner:
    _subprocess = None
    _ready_message = "***LSP READY***"
    _read_port = DEFAULT_SCLANG_LSP_CLIENTPORT
    _write_port = DEFAULT_SCLANG_LSP_SERVERPORT
    _udp_server = None
    _udp_client = None
    _stdin_thread = None

    def __init__(self, read_port: int | None, write_port: int | None):
        if read_port:
            self._read_port = read_port
        if write_port:
            self._write_port = write_port

    async def start_supercollider_subprocess(self, sc_lang_path: str, sc_config_path: str, ide_name: str):
        if self._subprocess:
            self._stop_subprocess()

        my_env = os.environ.copy()

        additional_vars = {
            "SCLANG_LSP_ENABLE": "1",
            "SCLANG_LSP_CLIENTPORT": str(self._write_port),
            "SCLANG_LSP_SERVERPORT": str(self._read_port),
        }
        logger.info(f"RUNNER: SC env vars: {additional_vars}")

        command = [sc_lang_path, "-i", ide_name, "-l", sc_config_path]
        logger.info(f"RUNNER: Launching SC with cmd: {command}")

        self._subprocess = await asyncio.create_subprocess_exec(
            *command,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
            stdin=asyncio.subprocess.PIPE,  # stdin must be set to PIPE so stdin flows to the main program
            env={**my_env, **additional_vars},
        )

        # receive stdout and stderr from sclang
        if self._subprocess.stdout and self._subprocess.stderr:
            await asyncio.gather(
                self._receive_output(self._subprocess.stdout, "SC:STDOUT"),
                self._receive_output(self._subprocess.stderr, "SC:STDERR"),
            )

    async def _start_communication_to_sc(self):
        """
        Starts communication to sclang
        - Starts a UDP client to write messages to sclang
        - Starts a thread which reads stdin of the main python program
        - Sends data from stdin to the UDP client
        """
        transport, _ = await asyncio.get_event_loop().create_datagram_endpoint(
            lambda: UDPProtocol(), remote_addr=(LOCALHOST, self._write_port)
        )
        self._udp_client = transport

        def on_stdin_received(line):
            transport.sendto(line.strip().encode())

        self._stdin_thread = StdinThread(on_stdin_received)
        self._stdin_thread.start()
        logger.info(f"RUNNER: UDP Sender running on {LOCALHOST}:{self._write_port}")

    async def _start_communication_from_sc(self):
        """Starts a UDP server to listen to messages from SC. Passes these messages to stdout."""
        transport, _ = await asyncio.get_event_loop().create_datagram_endpoint(
            lambda: UDPProtocol(), local_addr=(LOCALHOST, self._read_port)
        )
        self._udp_server = transport

    def _stop_subprocess(self):
        if self._subprocess and self._subprocess.returncode is None:
            self._subprocess.terminate()

    async def _receive_output(self, stream: StreamReader, prefix: str):
        async for line in stream:
            output = line.decode().rstrip()

            if output:
                logger.info(f"{prefix}: {output}")

            if self._ready_message in output:
                logger.info("RUNNER: ready message received")
                asyncio.create_task(self._start_communication_to_sc())
                asyncio.create_task(self._start_communication_from_sc())

    def stop(self):
        self._stop_subprocess()

        if self._stdin_thread:
            self._stdin_thread.close()
        if self._udp_client:
            self._udp_client.close()
        if self._udp_server:
            self._udp_server.close()


def main():
    parser = argparse.ArgumentParser(
        prog="sclsp_runner",
        description="Runs the SuperCollider LSP server and provides stdin/stdout access to it",
    )

    parser.add_argument("--sc-lang-path", action="store", type=str, required=True)
    parser.add_argument("--sc-config-path", action="store", type=str, required=True)
    parser.add_argument("--sc-server-port", action="store", type=int)
    parser.add_argument("--sc-client-port", action="store", type=int)
    parser.add_argument("--ide-name", action="store", type=str, default="external")

    args = parser.parse_args()

    lsp_runner = LSPRunner(read_port=args.sc_server_port, write_port=args.sc_client_port)

    def signal_handler(signum, frame):
        logger.info("RUNNER: Received termination signal")
        lsp_runner.stop()

    # Register signal handlers for termination signals
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)

    asyncio.run(
        lsp_runner.start_supercollider_subprocess(args.sc_lang_path, args.sc_config_path, args.ide_name)
    )


if __name__ == "__main__":
    main()
