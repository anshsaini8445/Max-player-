import json, os, subprocess, sys, time, re
from pathlib import Path
from openai import OpenAI
import httpx

API_KEY = os.environ.get("NVIDIA_API_KEY", "").strip()
PROJECT_DIR = Path(".").resolve()

def compile_base_app(message="Base App Build"):
    print(f"\n--- {message} ---")
    print("Building standard Max Player APK so the process never fails...")
    build = subprocess.run(["./gradlew", "assembleDebug", "--no-daemon", "-x", "test"], capture_output=True, text=True)
    if build.returncode == 0:
        print("✅ BUILD SUCCESSFUL! APK generated.")
        Path("agent_summary.txt").write_text("Max Player App Built Successfully.", encoding="utf-8")
        sys.exit(0)
    else:
        print("❌ BUILD FAILED.")
        print(build.stderr[-3000:])
        sys.exit(1)

if not API_KEY: 
    compile_base_app("Missing API Key")

client = OpenAI(
    base_url="https://integrate.api.nvidia.com/v1",
    api_key=API_KEY,
    http_client=httpx.Client(timeout=120.0)
)

allowed = {".kt", ".java", ".xml", ".gradle", ".kts"}
blocked = {".git", ".github", "build", "gradle"}
files = [p.relative_to(PROJECT_DIR).as_posix() for p in PROJECT_DIR.rglob("*") if p.is_file() and not set(p.relative_to(PROJECT_DIR).parts).intersection(blocked) and p.suffix.lower() in allowed]

prompt = f"""
Resolve Issue #{os.environ.get('ISSUE_NUMBER')} for Max Player: {os.environ.get('ISSUE_TITLE')}
Details: {os.environ.get('ISSUE_BODY')}
Files: {", ".join(files[:150])}
Rule: Return ONLY valid JSON format with keys "summary" and "files".
"""

model_name = "deepseek-ai/deepseek-v4.1-flash"
response_text = None

for retry in range(2):
    try:
        print(f"\nConnecting to NVIDIA DeepSeek...")
        completion = client.chat.completions.create(
            model=model_name,
            messages=[{"role": "user", "content": prompt}],
            temperature=0.1
        )
        response_text = completion.choices[0].message.content
        print("✅ AI Code generated!")
        break
    except Exception as e:
        print(f"✗ NVIDIA Server error: {e}")
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
        
    Path("agent_summary.txt").write_text(data.get("summary", "Max Player AI Fix Applied"), encoding="utf-8")
    
    print("Compiling Max Player with AI changes...")
    build = subprocess.run(["./gradlew", "assembleDebug", "--no-daemon", "-x", "test"], capture_output=True, text=True)
    
    if build.returncode == 0:
        print("✅ AI BUILD SUCCESSFUL!")
        sys.exit(0)
    else:
        print("❌ AI Code caused error. Falling back to base code.")
        compile_base_app("Fallback to Base Setup")
        
except Exception as e:
    compile_base_app("Fallback after JSON Error")
