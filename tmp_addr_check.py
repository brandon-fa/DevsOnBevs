import sqlite3
import subprocess
import os

c = sqlite3.connect(r"database/littleisle.sqlite")
print("--- hex addresses ---")
for row in c.execute(
    "SELECT id, delivery_address, hex(delivery_address) FROM orders WHERE id IN (1048, 1052)"
):
    print(row)
print("default:", c.execute("SELECT default_address, hex(default_address) FROM users WHERE id=16").fetchone())

# Compile + run a tiny locate check via jshell or java
os.chdir("backend")
src = r"""
import com.littleisles.domain.ord.*;
public class LocateCheck {
  public static void main(String[] args) {
    CityBounds b = new CityBounds(45.45, 45.56, -73.68, -73.40);
    for (String a : new String[]{
      "6700 Grande Allée",
      "6700 Grande Allee",
      "6700 Avenue Auteuil",
      "6700 avenue auteuil",
      "4918 Allee des Sorbiers"
    }) {
      try {
        double[] p = AddressLocator.locate(a, b);
        System.out.println(a + " -> " + p[0] + ", " + p[1]);
      } catch (Exception e) {
        System.out.println(a + " -> ERR " + e.getMessage());
      }
    }
  }
}
"""
open("LocateCheck.java", "w", encoding="utf-8").write(src)
print(
    subprocess.run(
        ["javac", "-cp", "target/classes;target/dependency/*", "-d", "target/classes", "LocateCheck.java"],
        capture_output=True,
        text=True,
    )
)
# find gson jar
import glob
jars = glob.glob("target/dependency/*.jar") + glob.glob(
    os.path.expanduser("~/.m2/repository/com/google/code/gson/gson/*/*.jar")
)
cp = "target/classes"
for j in jars:
    if "gson" in j.replace("\\", "/").lower():
        cp += ";" + j
        break
print("cp", cp)
print(subprocess.run(["java", "-cp", cp, "LocateCheck"], capture_output=True, text=True))
