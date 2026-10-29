"""Inventory official IDE modules/action anchors and compile every production source against them.

This is static API verification, not an interactive IDE smoke test.
"""
import argparse
import json
import os
import pathlib
import subprocess
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("ides", nargs="+", type=pathlib.Path)
    parser.add_argument("--java-home", type=pathlib.Path,
                        default=pathlib.Path("C:/Program Files/Android/Android Studio Panda 3/jbr"))
    args = parser.parse_args()
    root = pathlib.Path(__file__).resolve().parent.parent
    report_root = root / "build" / "ide-api-results"
    report_root.mkdir(parents=True, exist_ok=True)
    imports = sorted({line.strip()[7:-1] for source in (root / "src/main/java").rglob("*.java")
                      for line in source.read_text(encoding="utf-8-sig").splitlines()
                      if line.startswith(("import com.intellij.", "import git4idea."))})
    sources = sorted((root / "src/main/java").rglob("*.java"))
    results = []
    for ide in args.ides:
        product = json.loads((ide / "product-info.json").read_text(encoding="utf-8-sig"))
        code = product["productCode"]
        key = code + "-" + product["buildNumber"]
        output = report_root / key
        output.mkdir(parents=True, exist_ok=True)
        jars = list((ide / "lib").rglob("*.jar")) + list((ide / "plugins/vcs-git").rglob("*.jar"))
        classpaths = [str(jar.resolve()) for jar in jars]
        found = {}
        descriptors = []
        for jar in jars:
            with zipfile.ZipFile(jar) as archive:
                names = set(archive.namelist())
                for name in imports:
                    if name.replace(".", "/") + ".class" in names:
                        found[name] = jar.relative_to(ide).as_posix()
                if "vcs-git" in str(jar):
                    descriptors.extend(archive.read(name).decode("utf-8", errors="replace")
                                       for name in names if name.endswith(".xml"))
        descriptor_text = "\n".join(descriptors)
        anchors = {name: f'id="{name}"' in descriptor_text
                   for name in ("Git.MainMenu", "Git.Branch.Backend", "Git.CreateNewWorkingTree")}
        external = {name: found.get(name) for name in imports if "externalSystem" in name}
        # javac argument files avoid the Windows command-line length limit.
        arguments = ["-encoding", "UTF-8", "-proc:none", "-classpath", os.pathsep.join(classpaths),
                     "-d", str(output / "classes")] + [str(source) for source in sources]
        argfile = output / "compile.args"
        argfile.write_text("\n".join('"' + value.replace("\\", "/").replace('"', '\\"') + '"'
                                     for value in arguments), encoding="utf-8")
        compiled = subprocess.run([str(args.java_home / "bin/javac.exe"), "@" + str(argfile)],
                                  capture_output=True, encoding="utf-8", errors="replace")
        (output / "compile.log").write_text(compiled.stdout + compiled.stderr, encoding="utf-8")
        result = {"product": product["name"], "code": code, "version": product["version"],
                  "build": product["buildNumber"], "sourceCount": len(sources),
                  "compilePassed": compiled.returncode == 0, "gitActionAnchors": anchors,
                  "missingImports": sorted(set(imports) - found.keys()), "externalSystemClasses": external,
                  "libraries": len(jars), "scope": "Static API/descriptor scan only; not UI execution"}
        results.append(result)
        (output / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps(result, ensure_ascii=False), flush=True)
    (report_root / "summary.json").write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    if any(not result["compilePassed"] or result["missingImports"] or not all(result["gitActionAnchors"].values())
           for result in results):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
