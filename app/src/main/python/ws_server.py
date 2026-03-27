#!/usr/bin/env python3
"""
Android Studio Mobile - WebSocket/HTTP Backend Server
Provides real-time communication between Kotlin frontend and Python backend.
Handles terminal output, build logs, Gradle sync status, and dependency management.
"""

import http.server
import json
import os
import subprocess
import sys
import threading
import socketserver
from urllib.parse import urlparse, parse_qs

PORT = 8391
SDK_BASE = os.environ.get("RVK_SDK_DIR", "/storage/emulated/0/AndroidStudioMobile/sdk")
PROJECTS_DIR = os.environ.get("RVK_PROJECTS_DIR", "/storage/emulated/0/AndroidStudioMobile/Projects")


class BuildManager:
    """Manages Gradle builds and dependency resolution."""

    def __init__(self):
        self.current_build = None
        self.build_output = []
        self.is_building = False

    def start_build(self, project_path, build_type="assembleDebug"):
        if self.is_building:
            return {"error": "Build already in progress"}

        self.is_building = True
        self.build_output = []

        thread = threading.Thread(
            target=self._run_build,
            args=(project_path, build_type)
        )
        thread.daemon = True
        thread.start()

        return {"status": "started", "build_type": build_type}

    def _run_build(self, project_path, build_type):
        try:
            gradlew = os.path.join(project_path, "gradlew")

            # Ensure gradlew is executable (chmod 755)
            if os.path.exists(gradlew):
                os.chmod(gradlew, 0o755)
                self.build_output.append(f"chmod 755 {gradlew}")

            # Set up environment
            env = os.environ.copy()
            jdk17 = os.path.join(SDK_BASE, "jdk17")
            jdk21 = os.path.join(SDK_BASE, "jdk21")

            java_home = jdk17 if os.path.exists(jdk17) else jdk21
            if os.path.exists(java_home):
                # Check for nested directory
                for item in os.listdir(java_home):
                    nested = os.path.join(java_home, item)
                    if os.path.isdir(nested) and os.path.exists(os.path.join(nested, "bin", "java")):
                        java_home = nested
                        break
                env["JAVA_HOME"] = java_home
                env["PATH"] = f"{java_home}/bin:{env.get('PATH', '/usr/bin:/bin')}"

            sdk_tools = os.path.join(SDK_BASE, "sdk-tools")
            if os.path.exists(sdk_tools):
                env["ANDROID_HOME"] = sdk_tools
                env["ANDROID_SDK_ROOT"] = sdk_tools

            # Try direct execution first
            cmd = [gradlew, build_type]
            try:
                process = subprocess.Popen(
                    cmd,
                    cwd=project_path,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    env=env,
                    text=True
                )
            except PermissionError:
                # Fallback to sh
                self.build_output.append("Permission denied, falling back to: sh gradlew")
                cmd = ["sh", gradlew, build_type]
                process = subprocess.Popen(
                    cmd,
                    cwd=project_path,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    env=env,
                    text=True
                )

            for line in process.stdout:
                self.build_output.append(line.rstrip())

            process.wait()
            exit_code = process.returncode

            if exit_code == 0:
                # Find APK
                apk_path = self._find_apk(project_path, build_type)
                self.build_output.append(f"\n=== BUILD SUCCESSFUL ===")
                if apk_path:
                    self.build_output.append(f"APK: {apk_path}")
            else:
                self.build_output.append(f"\n=== BUILD FAILED (exit code: {exit_code}) ===")

        except Exception as e:
            self.build_output.append(f"BUILD ERROR: {str(e)}")
        finally:
            self.is_building = False

    def _find_apk(self, project_path, build_type):
        build_dir = "debug" if "debug" in build_type.lower() else "release"
        search_dirs = [
            os.path.join(project_path, "app", "build", "outputs", "apk", build_dir),
            os.path.join(project_path, "app", "build", "outputs", "apk"),
            os.path.join(project_path, "build", "outputs", "apk", build_dir),
        ]
        for d in search_dirs:
            if os.path.exists(d):
                for f in os.listdir(d):
                    if f.endswith(".apk"):
                        return os.path.join(d, f)
        return None

    def sync_dependencies(self, project_path):
        """Sync Gradle dependencies with real-time resolution."""
        self.build_output = []
        gradlew = os.path.join(project_path, "gradlew")

        if os.path.exists(gradlew):
            os.chmod(gradlew, 0o755)

        env = os.environ.copy()
        jdk17 = os.path.join(SDK_BASE, "jdk17")
        if os.path.exists(jdk17):
            env["JAVA_HOME"] = jdk17
            env["PATH"] = f"{jdk17}/bin:{env.get('PATH', '')}"

        try:
            cmd = [gradlew, "dependencies", "--refresh-dependencies"]
            if not os.access(gradlew, os.X_OK):
                cmd = ["sh", gradlew, "dependencies", "--refresh-dependencies"]

            process = subprocess.Popen(
                cmd, cwd=project_path,
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                env=env, text=True
            )

            output_lines = []
            for line in process.stdout:
                output_lines.append(line.rstrip())

            process.wait()
            return {
                "status": "success" if process.returncode == 0 else "failed",
                "output": output_lines
            }
        except Exception as e:
            return {"status": "error", "message": str(e)}


class TerminalSession:
    """Manages interactive terminal sessions."""

    def __init__(self):
        self.history = []

    def execute(self, command, work_dir=None):
        if not work_dir:
            work_dir = PROJECTS_DIR

        self.history.append(command)

        try:
            result = subprocess.run(
                ["sh", "-c", command],
                cwd=work_dir,
                capture_output=True,
                text=True,
                timeout=60
            )
            output = result.stdout
            if result.stderr:
                output += result.stderr
            return {
                "output": output,
                "exit_code": result.returncode
            }
        except subprocess.TimeoutExpired:
            return {"output": "Command timed out after 60 seconds", "exit_code": -1}
        except Exception as e:
            return {"output": f"Error: {str(e)}", "exit_code": -1}


# Global instances
build_manager = BuildManager()
terminal = TerminalSession()


class RequestHandler(http.server.BaseHTTPRequestHandler):
    """HTTP request handler for the backend server."""

    def log_message(self, format, *args):
        pass  # Suppress default logging

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path

        if path == "/status":
            self._json_response({"status": "running", "version": "1.0.0"})
        elif path == "/sdk/status":
            self._json_response(self._get_sdk_status())
        elif path == "/build/output":
            self._json_response({
                "output": build_manager.build_output,
                "is_building": build_manager.is_building
            })
        elif path == "/projects":
            self._json_response(self._get_projects())
        else:
            self._json_response({"error": f"Unknown endpoint: {path}"}, 404)

    def do_POST(self):
        parsed = urlparse(self.path)
        path = parsed.path
        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length).decode("utf-8") if content_length > 0 else "{}"

        try:
            data = json.loads(body) if body else {}
        except json.JSONDecodeError:
            data = {}

        if path == "/terminal/execute":
            command = data.get("command", "")
            work_dir = data.get("work_dir", None)
            result = terminal.execute(command, work_dir)
            self._json_response(result)
        elif path == "/build/start":
            project_path = data.get("project_path", "")
            build_type = data.get("build_type", "assembleDebug")
            result = build_manager.start_build(project_path, build_type)
            self._json_response(result)
        elif path == "/build/sync":
            project_path = data.get("project_path", "")
            result = build_manager.sync_dependencies(project_path)
            self._json_response(result)
        else:
            self._json_response({"error": f"Unknown endpoint: {path}"}, 404)

    def _json_response(self, data, status=200):
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(json.dumps(data).encode("utf-8"))

    def _get_sdk_status(self):
        components = [
            "jdk17", "jdk21", "ndk", "sdk-tools",
            "gradle-8.14.4", "gradle-9.3.0", "gradle-9.4.0",
            "flutter", "nodejs", "python"
        ]
        status = {}
        for comp in components:
            comp_dir = os.path.join(SDK_BASE, comp)
            status[comp] = os.path.exists(comp_dir) and bool(os.listdir(comp_dir))
        return status

    def _get_projects(self):
        projects = []
        if os.path.exists(PROJECTS_DIR):
            for name in os.listdir(PROJECTS_DIR):
                full_path = os.path.join(PROJECTS_DIR, name)
                if os.path.isdir(full_path):
                    projects.append({
                        "name": name,
                        "path": full_path,
                        "has_gradle": os.path.exists(os.path.join(full_path, "build.gradle.kts")) or
                                      os.path.exists(os.path.join(full_path, "build.gradle"))
                    })
        return {"projects": projects}


def start_server(port=PORT):
    """Start the HTTP backend server."""
    with socketserver.TCPServer(("127.0.0.1", port), RequestHandler) as httpd:
        print(f"RVK Backend Server running on port {port}")
        httpd.serve_forever()


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else PORT
    start_server(port)
