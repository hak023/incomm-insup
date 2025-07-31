import sys
import socket
import json
from PyQt5.QtWidgets import (
    QApplication, QWidget, QVBoxLayout, QHBoxLayout, QPushButton,
    QTextEdit, QLabel, QComboBox, QLineEdit, QMessageBox
)
from PyQt5.QtCore import Qt

class AmasTcpClient(QWidget):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("AMAS PyQt5 TCP Client")
        self.resize(800, 600)
        self.setup_ui()

    def setup_ui(self):
        layout = QVBoxLayout()

        # IP/Port
        ip_port_layout = QHBoxLayout()
        ip_port_layout.addWidget(QLabel("IP:"))
        self.ip_input = QLineEdit("127.0.0.1")
        ip_port_layout.addWidget(self.ip_input)

        ip_port_layout.addWidget(QLabel("Port:"))
        self.port_input = QLineEdit("15021")
        ip_port_layout.addWidget(self.port_input)

        layout.addLayout(ip_port_layout)

        # Command
        cmd_layout = QHBoxLayout()
        cmd_layout.addWidget(QLabel("Command:"))
        self.cmd_combo = QComboBox()
        self.cmd_combo.addItems(["auth", "heartBeat", "execute"])
        self.cmd_combo.currentTextChanged.connect(self.fill_template)
        cmd_layout.addWidget(self.cmd_combo)
        layout.addLayout(cmd_layout)

        # Input JSON
        layout.addWidget(QLabel("Request JSON Body:"))
        self.json_input = QTextEdit()
        self.fill_template("auth")
        layout.addWidget(self.json_input)

        # Send button
        self.send_btn = QPushButton("Request")
        self.send_btn.clicked.connect(self.send_message)
        layout.addWidget(self.send_btn)

        # Response
        layout.addWidget(QLabel("Response"))
        self.response_output = QTextEdit()
        self.response_output.setReadOnly(True)
        layout.addWidget(self.response_output)

        self.setLayout(layout)

    def fill_template(self, cmd):
        template = {
            "auth": {
                "cmd": "auth",
                "accessKey": "test_user1",
                "reqNo": "test_1",
                "ipAddr": "127.0.0.1"
            },
            "heartBeat": {
                "cmd": "heartBeat",
                "sessionId": "abcde12345"
            },
            "execute": {
                "cmd": "execute",
                "command": "ping",
                "params": {"target": "127.0.0.1"}
            }
        }
        self.json_input.setPlainText(json.dumps(template[cmd], indent=2))

    def wrap_request(self, json_str):
        byte_len = len(json_str.encode('utf-8'))
        return f"({byte_len:08d}){json_str}"

    def send_message(self):
        ip = self.ip_input.text().strip()
        port = int(self.port_input.text().strip())
        try:
            json_body = json.loads(self.json_input.toPlainText())
            json_str = json.dumps(json_body, ensure_ascii=False)
        except Exception as e:
            QMessageBox.critical(self, "JSON error", f"Invalid JSON:\n{str(e)}")
            return

        wrapped_msg = self.wrap_request(json_str)
        print(wrapped_msg)

        try:
            with socket.create_connection((ip, port), timeout=5) as sock:
                sock.sendall(wrapped_msg.encode('utf-8'))
                response = sock.recv(4096).decode('utf-8')
                self.response_output.setPlainText(self.extract_body(response))
        except Exception as e:
            self.response_output.setPlainText(f"Error {str(e)}")

    def extract_body(self, raw: str) -> str:
        if len(raw) <= 10:
            return "Invaild format"
        try:
            return json.dumps(json.loads(raw[10:]), indent=2, ensure_ascii=False)
        except Exception:
            return raw[10:]


if __name__ == '__main__':
    app = QApplication(sys.argv)
    client = AmasTcpClient()
    client.show()
    sys.exit(app.exec_())
