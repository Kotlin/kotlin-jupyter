import socket

DEFAULT_DEBUG_PORT = 1044


def get_port_not_in_use(first_port) -> int:
    port_range = range(first_port, 9999)
    for port in port_range:
        if not is_port_in_use(port):
            return port

    raise RuntimeError("No port available in range {}".format(port_range))


def is_port_in_use(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        return s.connect_ex(('localhost', port)) == 0
