import json, os, subprocess, sys, time, re
from pathlib import Path
from openai import OpenAI
import httpx

API_KEY = os.environ.get("NVIDIA_API_KEY", "").strip()
PROJECT_DIR = Path(".").resolve()

def compile_base_app(message="Base App Build"):
    print(f"\n--- {message} ---")
    build = subprocess.run(["./gradlew", "assembleDebug", "--no-daemon", "-x", "test"], capture_output=True, text=True)
    if build.returncode == 0:
        print("✅ BUILD SUCCESSFUL! Clean Base APK generated.")
        Path("agent_summary.txt").write_text("Clean Base App Built successfully.", encoding="utf-8")
        sys.exit(0) # Error mukt exit
    else:
        print("❌ BUILD FAILED.")
        print(build.stderr) # Ab asli error dikhega
        sys.exit(1)

if not API_KEY: compile_base_app("Missing API Key")

client = OpenAI(base_url="https://integrate.api.nvidia.com/v1", api_key=API_KEY, http_client=httpx.Client(timeout=120.0))
allowed = {".kt", ".java", ".xml", ".gradle", ".kts"}
blocked = {".git", ".github", "build", "gradle"}
files = [p.relative_to(PROJECT_DIR).as_posix() for p in PROJECT_DIR.rglob("*") if p.is_file() and not set(p.relative_to(PROJECT_DIR).parts).intersection(blocked) and p.suffix.lower() in allowed]

prompt = f"""
Resolve Issue #{os.environ.get('ISSUE_NUMBER')}: {os.environ.get('ISSUE_TITLE')}
Details: {os.environ.get('ISSUE_BODY')}
Files: {", ".join(files[:150])}
Rule: Return ONLY valid JSON format with keys "summary" and "files".
"""

model_name = "deepseek-ai/deepseek-v4.1-flash"
response_text = None

for retry in range(2):
    try:
        print(f"Connecting to {model_name}...")
        completion = client.chat.completions.create(model=model_name, messages=[{"role": "user", "content": prompt}], temperature=0.1)
        response_text = completion.choices[0].message.content
        break
    except Exception as e:
        if retry == 0: time.sleep(5)
            
if not response_text:
    compile_base_app("NVIDIA Server Timeout")
    
try:
    raw_text = re.sub(r'\\(?!["\\/bfnrtu])', r'\\\\', response_text.strip())
    match = re.search(r"\{[\s\S]*\}", raw_text)
    data = json.loads(match.group(0) if match else raw_text)
    for item in data.get("files", []):
        target = (PROJECT_DIR / item["filepath"]).resolve()
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(item["content"], encoding="utf-8")
    print("Compiling AI changes...")
    build = subprocess.run(["./gradlew", "assembleDebug", "--no-daemon", "-x", "test"], capture_output=True, text=True)
    if build.returncode == 0:
        print("✅ AI BUILD SUCCESSFUL!")
        sys.exit(0)
    else:
        print("AI Failed, reverting to clean base.")
        compile_base_app("AI Code Error")
except Exception as e:
    compile_base_app("Fallback after JSON Error")
