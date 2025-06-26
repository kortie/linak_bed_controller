# Linak 침대 Bluetooth 제어 프로젝트

## 📋 개요

이 프로젝트는 Linak 모터를 사용하는 전동 침대를 Bluetooth Low Energy(BLE)를 통해 제어하기 위한 완전한 프로토콜 분석 및 구현 가이드입니다.

## 🚀 주요 기능

- ✅ **완전한 침대 제어**: 머리/발 부분 개별 또는 동시 조절
- ✅ **프리셋 관리**: 최대 4개의 사용자 정의 위치 저장/호출
- ✅ **실시간 모니터링**: 머리/발 부분의 실시간 높이 데이터
- ✅ **조명 제어**: 침대 하단 LED 조명 제어
- ✅ **안전 기능**: 즉시 정지 및 타임아웃 보호

## 🔧 기술 스택

- **통신 프로토콜**: Bluetooth Low Energy (BLE)
- **지원 플랫폼**: Android, iOS, ESP32 (ESPHome)
- **연결 방식**: GATT Write Request

## 📡 Bluetooth 프로토콜 사양

### 🔌 연결 정보

| 구분 | UUID |
|------|------|
| **제어 서비스** | `99fa0001-338a-1024-8a49-009c0215f78a` |
| **제어 특성** | `99fa0002-338a-1024-8a49-009c0215f78a` |
| **센서 서비스** | `99fa0020-338a-1024-8a49-009c0215f78a` |
| **머리 높이 센서** | `99fa0028-338a-1024-8a49-009c0215f78a` |
| **발 높이 센서** | `99fa0027-338a-1024-8a49-009c0215f78a` |

### 🎮 제어 명령어

#### 기본 모터 제어
```
둘 다 내리기     : 0x00 0x00
둘 다 올리기     : 0x01 0x00
발 내리기        : 0x08 0x00
발 올리기        : 0x09 0x00
머리 내리기      : 0x0a 0x00
머리 올리기      : 0x0b 0x00
정지            : 0xFF 0x00
```

#### 프리셋 제어
```
프리셋 1 실행    : 0x0e 0x00
프리셋 2 실행    : 0x0f 0x00
프리셋 3 실행    : 0x0c 0x00
프리셋 4 실행    : 0x44 0x00
```

#### 프리셋 저장
```
프리셋 1 저장    : 0x38 0x00
프리셋 2 저장    : 0x39 0x00
프리셋 3 저장    : 0x3a 0x00
프리셋 4 저장    : 0x45 0x00
```

#### 조명 제어
```
조명 켜기        : 0x92 0x00
조명 끄기        : 0x93 0x00
조명 토글        : 0x94 0x00
```

## 📊 센서 데이터 변환

### 높이 데이터 변환 공식

```cpp
// 머리 높이 변환
uint16_t raw_height = ((uint16_t)data[1] << 8) | data[0];
float head_height_cm = (raw_height / 5) / 5.0;

// 발 높이 변환  
uint16_t raw_height = ((uint16_t)data[1] << 8) | data[0];
float feet_height_cm = (raw_height / 6) / 5.0;

// 속도 센서
uint16_t raw_speed = ((uint16_t)data[3] << 8) | data[2];
float speed_cm_min = raw_speed / 100.0;
```

## 🏗️ 구현 예제

### Android (Kotlin)
```kotlin
class LinakBedController {
    companion object {
        const val SERVICE_UUID = "99fa0001-338a-1024-8a49-009c0215f78a"
        const val CONTROL_UUID = "99fa0002-338a-1024-8a49-009c0215f78a"
        const val HEAD_SENSOR_UUID = "99fa0028-338a-1024-8a49-009c0215f78a"
        const val FEET_SENSOR_UUID = "99fa0027-338a-1024-8a49-009c0215f78a"
    }
    
    // 머리 올리기
    fun raiseHead() {
        writeCommand(byteArrayOf(0x0b.toByte(), 0x00))
    }
    
    // 즉시 정지
    fun stop() {
        writeCommand(byteArrayOf(0xFF.toByte(), 0x00))
    }
}
```

### ESPHome (YAML)
```yaml
ble_client:
  - mac_address: "XX:XX:XX:XX:XX:XX"
    id: linak_bed
    
button:
  - platform: template
    name: "머리 올리기"
    on_press:
      - ble_client.ble_write:
          id: linak_bed
          service_uuid: 99fa0001-338a-1024-8a49-009c0215f78a
          characteristic_uuid: 99fa0002-338a-1024-8a49-009c0215f78a
          value: [0x0b, 0x00]
```

## ⚠️ 안전 주의사항

### 필수 안전 기능
- **타임아웃 보호**: 연속 제어 시 최대 실행 시간 제한
- **즉시 정지**: 사용자가 언제든 동작을 중단할 수 있는 UI
- **연결 모니터링**: BLE 연결 끊김 시 자동 정지
- **충돌 감지**: 예상치 못한 저항 감지 시 정지

### 구현 권장사항
```cpp
// 타임아웃 설정 예제
const int MAX_OPERATION_TIME = 30000; // 30초
unsigned long operationStartTime = millis();

if (millis() - operationStartTime > MAX_OPERATION_TIME) {
    stopAllMotors();
}
```

## 🔍 호환성 정보

### 지원 확인된 모델
- Linak 기반 전동 침대 (ESPHome 테스트 완료)
- TD4 컨트롤러 시리즈

### 주의사항
- 모델별로 명령어가 다를 수 있음
- 펌웨어 버전에 따른 차이 가능
- **실제 사용 전 반드시 해당 모델에서 테스트 필요**

## 📱 프로젝트 구조

```
linak-bed-controller/
├── README.md
├── docs/
│   ├── protocol-analysis.md
│   └── safety-guidelines.md
├── android/
│   ├── app/
│   └── build.gradle
├── esphome/
│   └── linak-bed.yaml
└── examples/
    ├── kotlin/
    ├── python/
    └── javascript/
```

## 🤝 기여하기

1. **Fork** 이 저장소
2. **Branch** 생성 (`git checkout -b feature/amazing-feature`)
3. **Commit** 변경사항 (`git commit -m 'Add amazing feature'`)
4. **Push** 브랜치 (`git push origin feature/amazing-feature`)
5. **Pull Request** 생성

## 📜 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다. 자세한 내용은 [LICENSE](LICENSE) 파일을 참조하세요.

## 🙏 크레딧

- **jascdk**: ESPHome 구현 및 프로토콜 리버스 엔지니어링
- **Duus (Jonas Duus)**: Bluetooth 트래픽 분석 및 초기 연구
- **Linak**: 원본 하드웨어 및 공식 앱

## 📞 지원

- **Issues**: [GitHub Issues](https://github.com/your-repo/issues)
- **Discussions**: [GitHub Discussions](https://github.com/your-repo/discussions)
- **Email**: your-email@example.com

## 🔄 업데이트 로그

### v1.0.0 (2024-06-26)
- ✅ 초기 프로토콜 분석 완료
- ✅ 전체 명령어 세트 문서화
- ✅ 센서 데이터 변환 공식 제공
- ✅ 안전 가이드라인 수립

---

⚡ **면책조항**: 이 프로젝트는 교육 및 연구 목적으로 제작되었습니다. 실제 사용 시 발생하는 모든 문제에 대한 책임은 사용자에게 있습니다. 항상 안전을 최우선으로 고려하여 사용하세요.