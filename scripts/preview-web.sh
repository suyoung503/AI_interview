#!/usr/bin/env bash
set +x
set -euo pipefail
cd "$(dirname "$0")/.."
report=target/surefire-reports/TEST-com.interview.WebAccessTest.xml
if [[ ! -f "$report" || ! -f target/test-classes/com/interview/WebPreview.class ]]; then
    echo "먼저 README의 웹 테스트 명령으로 WebAccessTest를 실행하세요." >&2
    exit 1
fi
classpath=$(python3 - "$report" <<'PY'
import sys
from xml.etree import ElementTree

print(ElementTree.parse(sys.argv[1]).find("./properties/property[@name='java.class.path']").get("value"))
PY
)
exec env -u GROQ_API_KEY -u INTERVIEW_ENV_FILE java -cp "$classpath" com.interview.WebPreview
