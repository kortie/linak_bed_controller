#!/usr/bin/env bash
# 검증 게이트 단일 진입점 (docs/spec/steps/S00-scaffolding.md, linak-verify 스킬).
#   scripts/verify.sh quick   린트 + 순수 로직(JVM) 단위 테스트
#   scripts/verify.sh full    quick + 전체 로컬 단위 테스트 + Android lint + assembleDebug + 정적 점검 + 커버리지
# 기기·에뮬레이터 없이 실행된다. 마지막 줄은 항상 `VERIFY <mode>: PASS|FAIL`.
set -uo pipefail
cd "$(dirname "$0")/.."

MODE="${1:-}"
FAILED=0

run() {
    echo "▶ $*"
    "$@" || FAILED=1
}

# 존재하는 경로만 남긴다 (아직 만들어지지 않은 모듈은 건너뜀).
existing() {
    local path
    for path in "$@"; do
        [ -e "$path" ] && echo "$path"
    done
}

# 위반이 있으면 grep 결과를 출력하고 실패 처리한다.
check() {
    local name="$1" pattern="$2" flags="$3"
    shift 3
    local paths
    paths=$(existing "$@")
    if [ -z "$paths" ]; then
        echo "- $name: 대상 없음 (건너뜀)"
        return
    fi
    local hits
    # shellcheck disable=SC2086
    hits=$(grep -rn $flags -E "$pattern" --include='*.kt' $paths | grep -v "app/src/main/kotlin/dev/linakbed/app/log/AndroidBedLogger.kt" || true)
    if [ -n "$hits" ]; then
        echo "✗ $name"
        echo "$hits"
        FAILED=1
    else
        echo "✓ $name"
    fi
}

# linak-verify 스킬 §3과 같은 내용. 한쪽을 바꾸면 다른 쪽도 고친다.
static_checks() {
    echo "▶ 정적 점검"
    check "도메인/프로토콜의 Android 의존 금지" \
        '^import android\.' "" \
        core/protocol/src core/domain/src
    check "명령 바이트 리터럴은 core:protocol 안에만" \
        '0x(0[0-9a-f]|3[89a]|4[45]|9[234]|ff)\b' "-i" \
        core/domain/src/main core/ble/src/main core/data/src/main integration/*/src/main
    check "integration:*는 ble/data를 직접 참조하지 않음" \
        'dev\.linakbed\.(ble|data)' "" \
        integration/*/src
    check "android.util.Log / println 직접 사용 금지 (AndroidBedLogger 제외)" \
        'android\.util\.Log|\bprintln\(' "" \
        core/protocol/src/main core/domain/src/main core/ble/src/main core/data/src/main integration/*/src/main app/src/main
    check "원격 명령 공통 층은 채널 층에 의존하지 않음 (ADR-006)" \
        'dev\.linakbed\.domain\.remote\.[a-z]+\.' "" \
        core/domain/src/main/kotlin/dev/linakbed/domain/remote/*.kt
}

case "$MODE" in
    quick)
        run ./gradlew ktlintCheck detekt :core:protocol:test :core:domain:test
        ;;
    full)
        run ./gradlew ktlintCheck detekt \
            :core:protocol:test :core:domain:test testDebugUnitTest \
            lintDebug assembleDebug koverVerify
        static_checks
        ;;
    *)
        echo "usage: scripts/verify.sh quick|full" >&2
        exit 2
        ;;
esac

if [ "$FAILED" -eq 0 ]; then
    echo "VERIFY $MODE: PASS"
else
    echo "VERIFY $MODE: FAIL"
    exit 1
fi
