#!/usr/bin/env python3
"""Genera un proyecto Quarkus desde code.quarkus.io o le agrega persistencia bajo demanda.

Uso:
    python3 generate_project.py [--dir <workspace>] [--min-version 3.40.1] [--dry-run]
    python3 generate_project.py add-persistence [--dir <workspace>]

Qué hace:
  1. Consulta https://code.quarkus.io/api/streams y elige el stream LTS FINAL más
     reciente que sea >= --min-version y compatible con Java 25.
  2. Descarga el proyecto (Maven, Java 25, con Maven Wrapper) solo con las extensiones base
     (sin persistencia: Panache y PostgreSQL se agregan después con `add-persistence`).
  3. Lo extrae en el workspace (artifactId = nombre del directorio) sin pisar archivos
     existentes como CLAUDE.md o .claude/.
  4. Ajusta el pom.xml a <packaging>jar</packaging> y asegura quarkus-arc explícito.
  5. Escribe application.properties y crea los packages base
     (controllers, services, repositories, entities, models/dto, mappers).

`add-persistence` agrega quarkus-hibernate-orm-panache y quarkus-jdbc-postgresql al pom
(vía ./mvnw quarkus:add-extension) y anexa la configuración de datasource/Hibernate
a application.properties. Es idempotente.
"""
import argparse
import io
import json
import os
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import urllib.parse
import zipfile

GROUP_ID = "com.andres.course.claude.quarkus"
JAVA_VERSION = 25
API = "https://code.quarkus.io"
# quarkus-arc no se puede pedir en code.quarkus.io (es transitivo); se agrega luego al pom.
EXTENSIONS = [
    "io.quarkus:quarkus-rest-jackson",
    "io.quarkus:quarkus-hibernate-validator",
    "io.quarkus:quarkus-smallrye-openapi",
]
# Solo bajo demanda (subcomando add-persistence).
PERSISTENCE_EXTENSIONS = ["io.quarkus:quarkus-hibernate-orm-panache", "io.quarkus:quarkus-jdbc-postgresql"]
BASE_PACKAGES = ["controllers", "services", "repositories", "entities", "models/dto", "mappers"]


def http_get(url):
    """GET con curl: evita depender de los certificados SSL del Python instalado."""
    with tempfile.NamedTemporaryFile(delete=False) as tmp:
        out = tmp.name
    try:
        r = subprocess.run(["curl", "-sSL", "-o", out, "-w", "%{http_code}", url],
                           capture_output=True, text=True)
        if r.returncode != 0:
            sys.exit(f"No se pudo conectar a {API}: {r.stderr.strip()}")
        body = open(out, "rb").read()
        if r.stdout.strip() != "200":
            sys.exit(f"code.quarkus.io respondió {r.stdout.strip()}: {body.decode(errors='replace')}")
        return body
    finally:
        os.remove(out)


def version_tuple(v):
    return tuple(int(x) for x in re.findall(r"\d+", v)[:4])


def pick_stream(min_version):
    streams = json.loads(http_get(f"{API}/api/streams"))
    candidates = [
        s for s in streams
        if s.get("status") == "FINAL"
        and s.get("lts")
        and JAVA_VERSION in s.get("javaCompatibility", {}).get("versions", [])
        and version_tuple(s["platformVersion"]) >= version_tuple(min_version)
    ]
    if not candidates:
        sys.exit(f"No hay stream LTS FINAL >= {min_version} compatible con Java {JAVA_VERSION}")
    return max(candidates, key=lambda s: version_tuple(s["platformVersion"]))


def package_segment(artifact_id):
    """Convierte el artifactId en un segmento de package Java válido.

    '2-quarkus-arc' -> 'quarkusarc' (minúsculas, sin guiones ni símbolos,
    sin dígitos iniciales porque un segmento de package no puede empezar con número).
    """
    seg = re.sub(r"[^a-z0-9]", "", artifact_id.lower())
    seg = re.sub(r"^[0-9]+", "", seg)
    if not seg:
        sys.exit(f"No se pudo derivar un package válido desde '{artifact_id}'")
    return seg


def download(artifact_id, stream_key):
    params = [
        ("g", GROUP_ID), ("a", artifact_id), ("v", "1.0.0-SNAPSHOT"),
        ("b", "MAVEN"), ("j", str(JAVA_VERSION)), ("S", stream_key), ("nc", "true"),
    ] + [("e", e) for e in EXTENSIONS]
    url = f"{API}/d?{urllib.parse.urlencode(params)}"
    return http_get(url)


def merge_gitignore(dest, generated):
    """Agrega al .gitignore existente las reglas de Quarkus que le falten (target/, etc.)."""
    current = open(dest, encoding="utf-8").read()
    existing = {l.strip() for l in current.splitlines()}
    missing = [l for l in generated.splitlines() if l.strip() and l.strip() not in existing]
    if missing:
        with open(dest, "a", encoding="utf-8") as f:
            f.write(("" if current.endswith("\n") else "\n") + "\n# Quarkus (code.quarkus.io)\n" + "\n".join(missing) + "\n")
    print(f"  - .gitignore existente: se agregaron {len(missing)} reglas de Quarkus")


def extract(zip_bytes, artifact_id, target):
    prefix = f"{artifact_id}/"
    with zipfile.ZipFile(io.BytesIO(zip_bytes)) as z:
        for info in z.infolist():
            if not info.filename.startswith(prefix) or info.is_dir():
                continue
            rel = info.filename[len(prefix):]
            dest = os.path.join(target, rel)
            if os.path.exists(dest):
                if rel == ".gitignore":
                    merge_gitignore(dest, z.read(info).decode("utf-8"))
                else:
                    print(f"  - se mantiene existente: {rel}")
                continue
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            with z.open(info) as src, open(dest, "wb") as out:
                shutil.copyfileobj(src, out)
            # conserva el bit ejecutable de mvnw
            mode = (info.external_attr >> 16) & 0o777
            if mode:
                os.chmod(dest, mode)
    mvnw = os.path.join(target, "mvnw")
    if os.path.exists(mvnw):
        os.chmod(mvnw, os.stat(mvnw).st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)


PLUGIN_EXECUTIONS = """<extensions>true</extensions>
                <executions>
                    <execution>
                        <goals>
                            <goal>build</goal>
                            <goal>generate-code</goal>
                            <goal>generate-code-tests</goal>
                            <goal>native-image-agent</goal>
                        </goals>
                    </execution>
                </executions>"""

ARC_DEPENDENCY = """<dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-arc</artifactId>
        </dependency>
        """


def patch_pom(target, artifact_id, requested_id):
    path = os.path.join(target, "pom.xml")
    pom = open(path, encoding="utf-8").read()
    if requested_id != artifact_id:
        pom = pom.replace(f"<artifactId>{requested_id}</artifactId>", f"<artifactId>{artifact_id}</artifactId>", 1)
    # code.quarkus.io genera <packaging>quarkus</packaging>; con jar el lifecycle
    # no enlaza los goals de Quarkus, por eso se declaran las executions.
    if "<packaging>jar</packaging>" not in pom:
        pom = re.sub(r"<packaging>\w+</packaging>", "<packaging>jar</packaging>", pom, count=1)
        if "<goal>build</goal>" not in pom:
            pom = pom.replace("<extensions>true</extensions>", PLUGIN_EXECUTIONS, 1)
    if "<artifactId>quarkus-arc</artifactId>" not in pom:
        pom = pom.replace("<dependency>\n            <groupId>io.quarkus</groupId>\n            <artifactId>quarkus-junit",
                          ARC_DEPENDENCY + "<dependency>\n            <groupId>io.quarkus</groupId>\n            <artifactId>quarkus-junit", 1)
    open(path, "w", encoding="utf-8").write(pom)


APPLICATION_PROPERTIES = """# ===== Aplicación =====
quarkus.application.name={artifact_id}
quarkus.http.port=8080

# ===== Packaging =====
quarkus.package.jar.enabled=true
quarkus.package.jar.type=fast-jar

# ===== Dev Mode: Live Coding, Hot Reload y Dev UI (./mvnw quarkus:dev) =====
# Live coding está activo por defecto en dev mode; Dev UI en http://localhost:8080/q/dev-ui
quarkus.live-reload.instrumentation=true
%dev.quarkus.log.console.level=INFO

# ===== OpenAPI / Swagger UI =====
quarkus.smallrye-openapi.path=/q/openapi
quarkus.swagger-ui.always-include=true
quarkus.swagger-ui.path=/q/swagger-ui
"""

PERSISTENCE_PROPERTIES = """
# ===== Datasource PostgreSQL =====
quarkus.datasource.db-kind=postgresql
# En dev/test, si no hay URL, Dev Services levanta PostgreSQL con Docker/Podman automáticamente.
# Para usar una base local en dev, descomenta y ajusta:
#%dev.quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/{db_name}
#%dev.quarkus.datasource.username=postgres
#%dev.quarkus.datasource.password=postgres
%prod.quarkus.datasource.jdbc.url=${{DB_URL:jdbc:postgresql://localhost:5432/{db_name}}}
%prod.quarkus.datasource.username=${{DB_USERNAME:postgres}}
%prod.quarkus.datasource.password=${{DB_PASSWORD:postgres}}

# ===== Hibernate ORM =====
%dev.quarkus.hibernate-orm.schema-management.strategy=drop-and-create
%test.quarkus.hibernate-orm.schema-management.strategy=drop-and-create
%prod.quarkus.hibernate-orm.schema-management.strategy=validate
%dev.quarkus.hibernate-orm.log.sql=true
"""


def write_properties(target, artifact_id, segment):
    path = os.path.join(target, "src/main/resources/application.properties")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    existing = open(path, encoding="utf-8").read().strip() if os.path.exists(path) else ""
    if existing:
        print("  - application.properties ya tiene contenido; no se sobrescribe")
        return
    with open(path, "w", encoding="utf-8") as f:
        f.write(APPLICATION_PROPERTIES.format(artifact_id=artifact_id))


def add_persistence(target):
    pom_path = os.path.join(target, "pom.xml")
    if not os.path.exists(pom_path):
        sys.exit("No existe pom.xml: genera primero el proyecto.")
    pom = open(pom_path, encoding="utf-8").read()
    missing = [e for e in PERSISTENCE_EXTENSIONS if f"<artifactId>{e.split(':')[1]}</artifactId>" not in pom]
    if missing:
        subprocess.run(["./mvnw", "-q", "-B", "quarkus:add-extension", f"-Dextensions={','.join(missing)}"],
                       cwd=target, check=True)
    else:
        print("  - extensiones de persistencia ya presentes en el pom")
    props_path = os.path.join(target, "src/main/resources/application.properties")
    props = open(props_path, encoding="utf-8").read() if os.path.exists(props_path) else ""
    if "quarkus.datasource.db-kind" in props:
        print("  - application.properties ya tiene datasource configurado")
    else:
        segment = package_segment(os.path.basename(target))
        with open(props_path, "a", encoding="utf-8") as f:
            f.write(PERSISTENCE_PROPERTIES.format(db_name=segment))
    print("Persistencia lista: Hibernate ORM Panache + PostgreSQL (Dev Services en dev mode).")


def create_packages(target, base_package):
    root = os.path.join(target, "src/main/java", *base_package.split("."))
    for pkg in BASE_PACKAGES:
        d = os.path.join(root, pkg)
        os.makedirs(d, exist_ok=True)
        if not os.listdir(d):
            open(os.path.join(d, ".gitkeep"), "w").close()
    os.makedirs(os.path.join(target, "src/test/java", *base_package.split(".")), exist_ok=True)
    return root


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("command", nargs="?", choices=["generate", "add-persistence"], default="generate")
    ap.add_argument("--dir", default=os.getcwd(), help="Directorio workspace (su nombre es el artifactId)")
    ap.add_argument("--min-version", default="3.40.1")
    ap.add_argument("--dry-run", action="store_true", help="Solo muestra lo que haría")
    args = ap.parse_args()

    target = os.path.abspath(args.dir)
    if args.command == "add-persistence":
        add_persistence(target)
        return
    artifact_id = os.path.basename(target)
    segment = package_segment(artifact_id)
    base_package = f"{GROUP_ID}.{segment}.app"
    stream = pick_stream(args.min_version)

    print(f"groupId:      {GROUP_ID}")
    print(f"artifactId:   {artifact_id}")
    print(f"package base: {base_package}")
    print(f"Quarkus:      {stream['platformVersion']} ({stream['key']}, LTS)")
    print(f"Java:         {JAVA_VERSION}")
    if args.dry_run:
        return

    if os.path.exists(os.path.join(target, "pom.xml")):
        sys.exit("Ya existe pom.xml en el workspace: el proyecto ya fue generado.")

    # code.quarkus.io exige ^[a-z][a-z0-9-._]*$; Maven acepta más (p. ej. '2-quarkus-arc'),
    # así que se pide con un id válido y luego se restaura el artifactId real en el pom.
    requested_id = artifact_id if re.fullmatch(r"[a-z][a-z0-9._-]*", artifact_id) else segment
    extract(download(requested_id, stream["key"]), requested_id, target)
    patch_pom(target, artifact_id, requested_id)
    write_properties(target, artifact_id, segment)
    root = create_packages(target, base_package)
    print(f"Proyecto generado. Código fuente en: {os.path.relpath(root, target)}")
    print("Siguiente paso: ./mvnw quarkus:dev")


if __name__ == "__main__":
    main()
